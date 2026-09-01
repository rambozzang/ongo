package com.ongo.application.webhook

import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/** `webhook_events.status` 값. 문자열 리터럴이 여기저기 흩어지면 오타 하나로 게이트가 열린다. */
object WebhookEventStatus {
    const val PENDING = "PENDING"
    const val PROCESSED = "PROCESSED"
    const val FAILED = "FAILED"
    const val DEAD_LETTER = "DEAD_LETTER"
}

/**
 * 한 PG 의 재시도 범위와 재처리 방법.
 *
 * @param provider 로그에 남길 이름
 * @param lockId PG 마다 **달라야 한다.** 같으면 한쪽이 도는 동안 다른 쪽이 통째로 스킵된다
 * @param eventTypes 이 PG 가 소유한 이벤트 타입. `webhook_events` 는 공유 테이블이라
 *   이 목록이 곧 소유권 경계다
 * @param reprocess 재처리 진입점. 실패하면 예외를 던져야 한다
 */
data class WebhookRetrySpec(
    val provider: String,
    val lockId: Long,
    val eventTypes: Set<String>,
    val reprocess: (WebhookEvent) -> Unit,
)

/**
 * 실패한 웹훅의 재시도 상태 기계.
 *
 * Paddle 과 포트원이 **같은 규칙**을 쓴다. 규칙을 각자 구현하면 한쪽만 고쳐지는 일이 생기는데,
 * 여기 담긴 것은 전부 틀리면 돈이 어긋나는 것들이다.
 *
 * - 방치된 `PENDING` 되살리기(수신 기록은 커밋됐는데 처리가 끝나지 않은 경우)
 * - `2^n` 분 백오프와 `max_retries` 초과 시 DEAD_LETTER
 * - 실패 기록이 **PROCESSED 를 덮지 않게** 하는 조건부 갱신
 * - 성공 표시는 낡은 스냅샷으로 행 전체를 덮지 않고 상태·완료시각만 변경
 *
 * 재처리 **방법**만 PG 마다 다르므로 [WebhookRetrySpec.reprocess] 로 받는다.
 */
@Component
class WebhookRetryRunner(
    private val webhookEventRepository: WebhookEventRepository,
    private val distributedLockPort: DistributedLockPort,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun run(spec: WebhookRetrySpec) {
        // tryLock/releaseLock 은 획득과 해제가 다른 커넥션에서 일어나 락이 누수된다.
        // PostgreSQL 자문 락은 세션 범위라 다른 커넥션에서 해제해도 풀리지 않는다.
        val ran = distributedLockPort.withLock(spec.lockId) { retryPendingWebhooks(spec) }
        if (!ran) log.debug("다른 인스턴스에서 {} 웹훅 재처리 실행 중, 스킵", spec.provider)
    }

    private fun retryPendingWebhooks(spec: WebhookRetrySpec) {
        val now = LocalDateTime.now()
        recoverStalePendingEvents(spec, now)

        /*
         * 소유권 조건을 **조회에도** 건다. `webhook_events` 는 여러 PG 가 공유하는 테이블이라
         * 상태만으로 고르면 남의 FAILED 행이 딸려 온다. 아래 루프는 집어든 행을 전부
         * 이 PG 의 재처리기로 넘기는데, 형식이 다르면 조용히 no-op 한 뒤 곧바로 PROCESSED 로
         * 찍힌다. 처리된 적 없는 결제 웹훅이 완료로 남는다.
         */
        val retryableEvents = webhookEventRepository.findRetryable(now, spec.eventTypes)
        if (retryableEvents.isEmpty()) return

        log.info("{} 웹훅 재처리 시작. 대상 이벤트 수: {}", spec.provider, retryableEvents.size)

        var successCount = 0
        var failCount = 0

        for (event in retryableEvents) {
            try {
                spec.reprocess(event)

                /*
                 * 성공 표시는 `event_id` 로 **상태와 완료 시각만** 바꾼다.
                 *
                 * `update(event.copy(...))` 는 집어들 때의 낡은 스냅샷으로 retry_count·
                 * next_retry_at·error_message 까지 함께 덮어쓴다. 그 사이에 인바운드 재전달이
                 * 같은 행을 갱신했다면 남의 최신 값을 낡은 값으로 되돌리는 셈이다.
                 */
                webhookEventRepository.markProcessed(event.eventId, LocalDateTime.now())
                successCount++
                log.info("웹훅 재처리 성공: eventId={}, eventType={}", event.eventId, event.eventType)
            } catch (e: Exception) {
                recordFailure(event, e)
                failCount++
            }
        }

        log.info("{} 웹훅 재처리 완료. 성공: {}, 실패: {}", spec.provider, successCount, failCount)
    }

    /**
     * 실패 기록은 **완료를 덮지 않는다.** 인바운드 재전달이 같은 이벤트를 먼저 처리해
     * PROCESSED 로 만들었을 수 있다. 무조건 덮으면 이미 반영된 처리가 다시 재시도 대상이 되어
     * 환불 크레딧 회수처럼 멱등하지 않은 처리가 두 번 실행된다.
     */
    private fun recordFailure(event: WebhookEvent, e: Exception) {
        val newRetryCount = event.retryCount + 1

        if (newRetryCount >= event.maxRetries) {
            webhookEventRepository.updateIfNotProcessed(
                event.copy(
                    status = WebhookEventStatus.DEAD_LETTER,
                    retryCount = newRetryCount,
                    errorMessage = e.message?.take(500),
                ),
            )
            log.error(
                "웹훅 최대 재시도 초과, DEAD_LETTER 처리: eventId={}, retryCount={}",
                event.eventId, newRetryCount, e,
            )
            return
        }

        val nextRetryAt = LocalDateTime.now().plusMinutes((1L shl minOf(newRetryCount, 5)).toLong())
        webhookEventRepository.updateIfNotProcessed(
            event.copy(
                status = WebhookEventStatus.FAILED,
                retryCount = newRetryCount,
                nextRetryAt = nextRetryAt,
                errorMessage = e.message?.take(500),
            ),
        )
        log.warn(
            "웹훅 재처리 실패: eventId={}, retryCount={}/{}, nextRetryAt={}",
            event.eventId, newRetryCount, event.maxRetries, nextRetryAt, e,
        )
    }

    /**
     * 수신만 기록되고 끝나지 않은 `PENDING` 이벤트를 재시도 대기열로 되돌린다.
     *
     * 수신 기록은 업무 트랜잭션과 분리해 먼저 커밋된다. 덕분에 실패해도 흔적이 남지만, 그
     * 사이에 프로세스가 죽거나 `catch (e: Exception)` 이 못 잡는 `Error` 가 나면 **FAILED 로
     * 옮길 코드 자체가 실행되지 않는다.** 그렇게 남은 `PENDING` 행은 `findRetryable` 의
     * `status = 'FAILED'` 조건에 걸리지 않아 영원히 재처리되지 않는다.
     *
     * 되살리기만 하고 처리는 위 재시도 경로에 맡긴다. 재처리 코드가 둘로 갈라지지 않고,
     * 횟수·백오프·DEAD_LETTER 도 같은 규칙을 그대로 쓴다.
     */
    private fun recoverStalePendingEvents(spec: WebhookRetrySpec, now: LocalDateTime) {
        val recovered = webhookEventRepository.recoverStalePending(
            olderThan = now.minusMinutes(STALE_PENDING_AGE_MINUTES),
            retryAt = now,
            eventTypes = spec.eventTypes,
            limit = RECOVERY_BATCH_LIMIT,
        )
        if (recovered == 0) return

        log.warn(
            "처리가 끝나지 않은 {} 웹훅 {}건을 재시도 대상으로 되살렸습니다. 수신 후 {}분이 지난 PENDING 이벤트입니다",
            spec.provider, recovered, STALE_PENDING_AGE_MINUTES,
        )
        // 한 틱에 쏟아지지 않도록 잘랐다는 사실을 남긴다. 조용히 자르면 "다 복구했다"로 읽힌다.
        if (recovered == RECOVERY_BATCH_LIMIT) {
            log.warn(
                "되살리기 상한({})에 도달했습니다. 남은 PENDING 이 있으면 다음 실행에서 이어서 처리합니다",
                RECOVERY_BATCH_LIMIT,
            )
        }
    }

    companion object {
        /**
         * 이 시간이 지난 `PENDING` 만 되살린다.
         *
         * 수신 처리는 HTTP 요청 하나로 끝난다 — PG API 재조회 한 번을 포함해도 초 단위다.
         * 30분은 그보다 세 자릿수 크므로 **처리 중인 정상 수신을 가로챌 여지가 없다.** 동시에
         * PG 자체 재전송이 먼저 복구할 시간도 준다. 그 경우 행은 PROCESSED 가 되어 여기
         * 조건에 애초에 걸리지 않는다.
         */
        const val STALE_PENDING_AGE_MINUTES = 30L

        /** 한 번에 되살릴 최대 건수. 적체가 한 틱에 외부 API 호출로 쏟아지는 것을 막는다. */
        const val RECOVERY_BATCH_LIMIT = 50
    }
}
