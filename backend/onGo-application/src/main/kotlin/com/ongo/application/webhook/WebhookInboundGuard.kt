package com.ongo.application.webhook

import com.ongo.domain.webhook.WebhookEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

/** 인바운드 웹훅 처리 결과. 호출자가 로그를 다르게 남기기 위해 구분한다. */
enum class WebhookInboundOutcome {
    PROCESSED,

    /** 다른 전달이 이미 끝냈다. 업무 처리를 하지 않았다. */
    ALREADY_PROCESSED,
}

/**
 * 인바운드 웹훅의 **수신 기록 · 동시성 잠금 · 실패 기록** 순서를 한곳에서 정한다.
 *
 * Paddle 과 포트원이 같은 구조를 쓴다. 순서가 틀리면 돈이 어긋나거나 요청이 멈추는데,
 * 그 순서를 각 PG 가 따로 구현하면 한쪽만 고쳐지는 일이 생긴다.
 *
 * ## 왜 업무 트랜잭션을 여기서 여는가 — 교착 회피
 *
 * 원래는 `handleWebhook` 자체가 `@Transactional` 이고 그 안에서
 * `findByEventIdForUpdate` 로 행을 잠근 뒤, `catch` 에서 REQUIRES_NEW 로 **같은 행**을
 * 갱신했다. 그 조합은 **자기 자신과 교착한다.**
 *
 * - 바깥 트랜잭션 T1 이 `SELECT ... FOR UPDATE` 로 행 잠금을 쥔 채 살아 있다.
 * - REQUIRES_NEW 는 T1 을 **커밋하지 않고 보류**한 뒤 다른 커넥션에서 T2 를 연다.
 * - T2 의 `UPDATE` 는 T1 이 쥔 잠금을 기다린다. T1 은 T2 가 끝나기를 기다린다.
 *
 * PostgreSQL 의 교착 감지는 **잠금 대기 그래프**에서 순환을 찾는다. 그런데 T1 은 잠금이
 * 아니라 애플리케이션 스레드를 기다리므로 그래프에 순환이 없다. 그래서
 * `deadlock_timeout` 이 발화하지 않고, `lock_timeout` 이 설정돼 있지 않으면 **무기한
 * 대기**한다. 커넥션 두 개가 묶인 채로.
 *
 * 해결은 대기 시간을 주는 것이 아니라 **구조를 바꾸는 것**이다. 업무 트랜잭션을 이 메서드
 * 안에서 열고 닫으면, `catch` 에 도달한 시점에는 롤백이 이미 끝나 **잠금이 풀려 있다.**
 * 실패 기록은 아무것도 기다리지 않는다.
 *
 * 그래서 호출하는 `handleWebhook` 에는 **`@Transactional` 을 붙이면 안 된다.** 붙이면
 * 아래 [businessTx] 가 그 트랜잭션에 참여해 경계가 다시 바깥으로 나가고 교착이 되살아난다.
 *
 * ## 결제 동시성 보호는 그대로다
 *
 * [dispatch] 는 [businessTx] 안에서 실행된다. 그래서 그 안에서 자기호출되는
 * `complete()` / `handleCancellation()` 의 `findByIdForUpdate` 행 잠금이 업무가 끝날 때까지
 * 유지된다 — 중복 지급·이중 회수 방어는 예전과 동일하다.
 */
@Component
class WebhookInboundGuard(
    private val webhookEventRepository: WebhookEventRepository,
    private val webhookEventRecorder: WebhookEventRecorder,
    transactionManager: PlatformTransactionManager,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 업무 트랜잭션. **여기서 열고 여기서 닫는다 — REQUIRES_NEW 로 강제한다.**
     *
     * 기본 전파(REQUIRED)로 두면 호출자가 트랜잭션을 열어 둔 순간 그것에 참여해 경계가
     * 바깥으로 나간다. 그러면 `catch` 시점에 행 잠금이 아직 살아 있어 교착이 그대로
     * 되살아난다 — 지금은 호출자가 무트랜잭션이라 우연히 동작할 뿐이고, 나중에 누가
     * `@Transactional` 을 붙이면 조용히 깨진다.
     *
     * REQUIRES_NEW 는 바깥 트랜잭션이 있든 없든 **항상 독립된 경계**를 만든다. 그래서 이
     * 클래스가 잠금의 수명을 혼자 책임진다.
     */
    private val businessTx = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    /**
     * @param dispatch 실제 업무 처리. 실패하면 예외를 던져야 한다.
     * @throws Exception [dispatch] 가 던진 예외를 그대로 다시 던진다. 호출자(컨트롤러)가
     *   2xx 가 아닌 응답을 돌려줘야 PG 가 재전송한다.
     */
    fun handle(
        eventId: String,
        eventType: String,
        payload: String,
        dispatch: () -> Unit,
    ): WebhookInboundOutcome {
        // 잠금 없는 빠른 경로. 이미 끝난 이벤트를 걸러낸다.
        val existing = webhookEventRepository.findByEventId(eventId)
        if (existing?.status == WebhookEventStatus.PROCESSED) return WebhookInboundOutcome.ALREADY_PROCESSED

        /*
         * 수신 기록은 별도 트랜잭션(REQUIRES_NEW)에 남긴다. 아직 아무 잠금도 잡지 않았으므로
         * 여기서는 교착이 없다. 이 기록이 있어야 처리에 실패해도 재시도 대상이 남는다.
         */
        val received = webhookEventRecorder.recordReceived(existing, eventId, eventType, payload)

        var outcome = WebhookInboundOutcome.PROCESSED
        /*
         * 실패를 기록할 때 **기준으로 삼을 이벤트**.
         *
         * [received] 를 그대로 쓰면 안 된다. `recordReceived` 는 삽입이 충돌해도(이미 있는
         * 이벤트) 다시 읽지 않고 방금 만든 객체를 돌려주므로 `retryCount` 가 0 이다. 그
         * 값으로 실패를 기록하면 DB 에 쌓인 재시도 횟수가 **1 로 초기화**된다. 그러면
         * `retry_count < max_retries` 가 영원히 참이라 DEAD_LETTER 에 도달하지 못하고 같은
         * 이벤트를 무한히 재시도한다.
         *
         * 그래서 잠금을 잡고 읽은 실제 행으로 바꿔 둔다. 잠금 전에 실패하면 그때는
         * [received] 밖에 아는 것이 없으므로 그대로 쓴다.
         */
        var failureBasis = received
        try {
            businessTx.executeWithoutResult {
                /*
                 * 같은 이벤트의 동시 전달을 행 잠금으로 직렬화한다. 수신 기록이 먼저
                 * 커밋되므로 두 번째 전달도 PROCESSED 가 아닌 행을 보고 들어온다.
                 * 잠근 뒤 상태를 **다시 확인**한다 — 상대의 완료는 잠금을 얻기 전에는
                 * 보이지 않는다.
                 */
                val claimed = webhookEventRepository.findByEventIdForUpdate(eventId) ?: received
                failureBasis = claimed
                if (claimed.status == WebhookEventStatus.PROCESSED) {
                    outcome = WebhookInboundOutcome.ALREADY_PROCESSED
                    return@executeWithoutResult
                }

                dispatch()

                /*
                 * 성공 표시는 **이 트랜잭션 안**에서 한다. 별도로 커밋하면 업무 쓰기가 뒤이어
                 * 롤백됐을 때 "처리 완료" 표시만 남아 영영 재처리되지 않는다.
                 *
                 * `event_id` 로 상태·완료시각만 바꾼다. 원자적 삽입은 id 를 돌려주지 않으므로
                 * 수신 직후의 이벤트에는 id 가 없다.
                 *
                 * 갱신 행수가 0이면 **예외를 던지지 않고 error 로그만 남긴다.** 0은 운영
                 * 조건이 아니라 키 불일치 같은 프로그래밍 오류를 뜻하고, 그 경우 모든 웹훅이
                 * 같은 지점에서 죽는다. 여기서 예외를 던지면 결제 반영 자체가 영구 실패한다.
                 * 이력 한 줄이 PENDING 으로 남는 쪽이 결제를 잃는 것보다 낫고, 그 PENDING 도
                 * 되살리기가 재시도 대기열로 옮긴다.
                 */
                if (!webhookEventRepository.markProcessed(eventId, LocalDateTime.now())) {
                    // 로그에 원문 본문·서명 헤더·시크릿은 절대 넣지 않는다. 식별자와 결과만 남긴다.
                    log.error(
                        "웹훅 이력 갱신 실패. eventId={} eventType={} outcome={}",
                        eventId, eventType, HISTORY_UPDATE_FAILED,
                    )
                }
            }
        } catch (e: Exception) {
            /*
             * **여기서는 업무 트랜잭션이 이미 롤백돼 행 잠금이 풀려 있다.** 그래서 아래
             * REQUIRES_NEW 기록이 아무것도 기다리지 않는다. 이 순서가 이 클래스의 존재 이유다.
             *
             * 업무 쓰기는 그대로 롤백된 상태다 — 부분 반영이 남으면 재시도가 이중 반영을 만든다.
             */
            webhookEventRecorder.recordFailure(failureBasis, e)
            throw e
        }

        return outcome
    }

    private companion object {
        const val HISTORY_UPDATE_FAILED = "HISTORY_UPDATE_FAILED"
    }
}
