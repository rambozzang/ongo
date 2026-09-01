package com.ongo.application.webhook

import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

/**
 * 웹훅 수신·실패 기록을 **업무 트랜잭션과 분리해** 남긴다. Paddle 과 포트원이 함께 쓴다.
 *
 * ## 왜 필요한가
 *
 * 인바운드 웹훅 처리는 실패 시 예외를 다시 던진다. 그 예외는 `RuntimeException` 이라 Spring
 * 기본 롤백 규칙이 적용되어, **같은 트랜잭션에 남긴 기록은 함께 롤백된다.** 처음 들어온
 * 이벤트라면 PENDING 저장까지 사라져 `webhook_events` 에 행이 아예 남지 않는다.
 *
 * 그러면 재처리 경로가 끊긴다. `findRetryable` 은 `status = 'FAILED'` 인 행만 고르므로,
 * 행이 없으면 재시도 스케줄러가 집을 대상이 없다. PG 자체 재전송이 소진되면 그 이벤트는
 * **아무 흔적 없이 사라진다** — DEAD_LETTER 도, 운영자가 볼 기록도 남지 않는다.
 *
 * ## 무엇을 어디에 커밋하는가
 *
 * - **수신(PENDING)**: 여기(REQUIRES_NEW). 업무 처리가 실패해도 "이 이벤트를 봤다" 는
 *   사실은 남아야 한다.
 * - **실패(FAILED)**: 여기(REQUIRES_NEW). 롤백을 견뎌야 재시도 대상이 된다.
 * - **성공(PROCESSED)**: **여기가 아니라 업무 트랜잭션 안**에서 갱신한다. 별도로 커밋하면
 *   업무 쓰기가 뒤이어 롤백됐을 때 "처리 완료" 표시만 남아 영영 재처리되지 않는다.
 *
 * ## 호출 시점 주의
 *
 * [recordFailure] 를 **행 잠금을 쥔 채로 부르면 안 된다.** REQUIRES_NEW 는 바깥 트랜잭션을
 * 커밋하지 않고 보류하므로, 같은 행을 갱신하려는 새 트랜잭션이 자기 자신의 잠금을 기다려
 * 무기한 멈춘다. 그 순서는 [WebhookInboundGuard] 가 책임진다.
 */
@Component
class WebhookEventRecorder(
    private val webhookEventRepository: WebhookEventRepository,
    transactionManager: PlatformTransactionManager,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 바깥 트랜잭션이 롤백돼도 이 기록은 남아야 한다. */
    private val requiresNewTx = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    /**
     * 이벤트 수신을 기록한다. 이미 있으면 그대로 쓴다(재시도·중복 수신).
     *
     * @return 이후 상태 갱신에 쓸 이벤트.
     */
    fun recordReceived(
        existing: WebhookEvent?,
        eventId: String,
        eventType: String,
        payload: String,
    ): WebhookEvent {
        if (existing != null) return existing
        return requiresNewTx.execute {
            /*
             * **원자적 삽입이어야 한다.** 같은 이벤트가 동시에 들어오면 둘 다 `existing == null`
             * 을 보고 여기 도착한다. 그냥 INSERT 하면 진 쪽이 유니크 위반을 맞고 그 트랜잭션이
             * 통째로 abort 된다 — 500 을 돌려주고 PG 재전송을 기다리는 셈이다.
             * `ON CONFLICT DO NOTHING` 으로 넣고, 넣었든 남이 넣었든 그 행을 읽어 쓴다.
             */
            val event = WebhookEvent(
                eventId = eventId,
                eventType = eventType,
                payload = payload,
                status = WebhookEventStatus.PENDING,
            )
            webhookEventRepository.saveIfAbsent(event)
            /*
             * id 를 되받지 않는다. 이후 상태 갱신은 전부 `event_id` 로 한다.
             *
             * 그래서 **이 반환값의 `retryCount` 는 0 이다** — 삽입이 충돌했더라도 DB 에 쌓인
             * 횟수를 읽어오지 않는다. 실패를 기록할 때 이 값을 그대로 쓰면 재시도 횟수가
             * 초기화되어 DEAD_LETTER 에 영영 도달하지 못한다. 호출자는 잠금으로 읽은 실제
             * 행을 기준으로 삼아야 한다([WebhookInboundGuard] 가 그렇게 한다).
             */
            event
        }!!
    }

    /**
     * 실패를 재시도 대상으로 기록한다. 백오프는 `2^n` 분(최대 32분)이다.
     *
     * 기록 자체가 실패해도 원래 예외를 가리지 않는다 — 호출자가 그것을 다시 던져야
     * 트랜잭션이 롤백되고 PG 가 재전송한다.
     *
     * **이미 `PROCESSED` 인 행은 건드리지 않는다.** 같은 이벤트가 동시에 전달되면 한쪽이
     * 완료를 커밋한 뒤 다른 쪽이 실패를 기록할 수 있다. 무조건 덮으면 이미 반영된 처리가
     * 재시도 대상으로 되살아나 환불 회수 같은 비멱등 처리가 두 번 실행된다.
     */
    fun recordFailure(event: WebhookEvent, error: Throwable) {
        val retryCount = event.retryCount + 1
        val nextRetry = LocalDateTime.now().plusMinutes((1L shl minOf(retryCount, 5)).toLong())
        requiresNewTx.execute {
            val recorded = webhookEventRepository.updateIfNotProcessed(
                event.copy(
                    status = WebhookEventStatus.FAILED,
                    retryCount = retryCount,
                    nextRetryAt = nextRetry,
                    errorMessage = error.message?.take(500),
                ),
            )
            if (!recorded) {
                log.info(
                    "이미 완료된 웹훅이라 실패 기록을 건너뜁니다. eventId={} eventType={}",
                    event.eventId, event.eventType,
                )
            }
        }
    }
}
