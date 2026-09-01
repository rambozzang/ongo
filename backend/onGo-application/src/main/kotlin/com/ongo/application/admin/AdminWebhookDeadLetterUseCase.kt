package com.ongo.application.admin

import com.ongo.application.paddle.PaddleWebhookService
import com.ongo.application.portone.PortOnePaymentService
import com.ongo.common.exception.BusinessException
import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** 운영 화면에 내보내는 DEAD_LETTER 웹훅 한 건. **원문 본문·서명은 절대 담지 않는다.** */
data class AdminDeadLetterWebhookItem(
    /** 재큐잉 대상 지정용 대리 키. `event_id` 를 노출하지 않기 위해 이것을 쓴다. */
    val id: Long,
    val provider: String,
    val eventType: String,
    /** 가운데를 가린 멱등 키. 같은 이벤트인지 대조할 만큼만 남긴다. */
    val maskedEventId: String,
    val retryCount: Int,
    val maxRetries: Int,
    val nextRetryAt: LocalDateTime?,
    /** 우리 예외 메시지를 길이 제한해 담는다. */
    val errorMessage: String?,
    val createdAt: LocalDateTime?,
    val processedAt: LocalDateTime?,
)

/** 재큐잉 결과. 건수를 지어내지 않고 **실제 갱신된 행**만 성공으로 본다. */
data class AdminDeadLetterRequeueResult(
    val id: Long,
    val status: String,
    val nextRetryAt: LocalDateTime,
)

/**
 * DEAD_LETTER 웹훅 조회와 **명시적 단건 재큐잉**.
 *
 * 재시도를 모두 소진한 이벤트는 어떤 스케줄러도 다시 집지 않는다(`findRetryable` 은 `FAILED`
 * 만 고른다). 그래서 결제·환불이 반영되지 않은 채 조용히 남고, 지금은 운영자가 그것을 볼
 * 방법조차 없다. 이 유스케이스가 그 조회와 복구를 담당한다.
 *
 * **자동 재처리는 하지 않는다.** 여기까지 온 이벤트는 이미 여러 번 실패했으므로 원인을
 * 사람이 확인해야 한다. 자동으로 되돌리면 같은 실패를 무한히 반복한다.
 */
@Service
class AdminWebhookDeadLetterUseCase(
    private val webhookEventRepository: WebhookEventRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun list(limit: Int): List<AdminDeadLetterWebhookItem> =
        webhookEventRepository.findDeadLettered(limit.coerceIn(1, MAX_LIST_LIMIT))
            .map { it.toAdminItem() }

    /**
     * 한 건을 재시도 대기열로 되돌린다.
     *
     * 판정은 전부 저장소의 조건부 갱신이 한다. 여기서 상태를 읽고 분기하면 그 사이에 바뀐
     * 행을 덮어쓰게 된다. 갱신 행수가 0이면 **성공으로 보고하지 않는다.**
     */
    @Transactional
    fun requeue(id: Long): AdminDeadLetterRequeueResult {
        val retryAt = LocalDateTime.now()
        val requeued = webhookEventRepository.requeueDeadLettered(id, retryAt, REQUEUEABLE_EVENT_TYPES)

        if (!requeued) {
            throw BusinessException(
                "WEBHOOK_REQUEUE_REJECTED",
                "재큐잉할 수 없는 이벤트입니다. 이미 처리됐거나, DEAD_LETTER 가 아니거나, " +
                    "우리가 재처리할 수 없는 이벤트입니다.",
            )
        }

        // 식별자와 결과만 남긴다. 원문 본문·서명은 절대 로그에 넣지 않는다.
        log.info("운영자가 DEAD_LETTER 웹훅을 재시도 대기열로 되돌렸습니다. id={}", id)

        /*
         * 되돌린 뒤의 행을 다시 읽어 담지 않는다. 그 시점에는 이미 DEAD_LETTER 가 아니라
         * 목록 조회로는 잡히지 않고, 없는 값을 채워 넣으면 화면이 사실이 아닌 것을 보여준다.
         * 갱신 결과만 그대로 돌려주고, 최신 상태는 호출자가 목록을 다시 불러 확인한다.
         */
        return AdminDeadLetterRequeueResult(
            id = id,
            status = "FAILED",
            nextRetryAt = retryAt,
        )
    }

    private fun WebhookEvent.toAdminItem() = AdminDeadLetterWebhookItem(
        id = id ?: 0L,
        provider = providerOf(eventType),
        eventType = eventType,
        maskedEventId = maskEventId(eventId),
        retryCount = retryCount,
        maxRetries = maxRetries,
        nextRetryAt = nextRetryAt,
        errorMessage = errorMessage?.take(ERROR_MESSAGE_LIMIT),
        createdAt = createdAt,
        processedAt = processedAt,
    )

    companion object {
        const val MAX_LIST_LIMIT = 200
        const val ERROR_MESSAGE_LIMIT = 200

        const val PROVIDER_PADDLE = "PADDLE"
        const val PROVIDER_PORTONE = "PORTONE"
        const val PROVIDER_UNKNOWN = "UNKNOWN"

        /**
         * 재큐잉을 허용할 이벤트 타입.
         *
         * 두 PG 가 소유한 타입의 합집합이다. 여기 없는 타입은 우리 재처리기가 해석하지
         * 못하므로 되돌려도 처리되지 않는다 — SQL 조건으로 막는다. 되돌린 뒤에는 각 PG
         * 스케줄러가 자기 타입만 집어가므로 자동으로 올바른 재처리기로 간다.
         */
        val REQUEUEABLE_EVENT_TYPES: Set<String> =
            PaddleWebhookService.REPROCESSABLE_EVENT_TYPES + PortOnePaymentService.REPROCESSABLE_EVENT_TYPES

        fun providerOf(eventType: String): String = when (eventType) {
            in PaddleWebhookService.REPROCESSABLE_EVENT_TYPES -> PROVIDER_PADDLE
            in PortOnePaymentService.REPROCESSABLE_EVENT_TYPES -> PROVIDER_PORTONE
            else -> PROVIDER_UNKNOWN
        }

        /**
         * 멱등 키를 가린다.
         *
         * 운영자가 PG 대시보드의 이벤트와 **대조할 수 있을 만큼만** 남기고 가운데를 가린다.
         * 전체를 노출하면 화면·로그·스크린샷을 통해 PG 이벤트 식별자가 그대로 퍼진다.
         *
         * 짧은 키는 앞뒤를 남기면 사실상 전부 보이므로 통째로 가린다.
         */
        fun maskEventId(eventId: String): String {
            if (eventId.length <= HEAD_KEEP + TAIL_KEEP) return "*".repeat(eventId.length)
            return eventId.take(HEAD_KEEP) + "…" + eventId.takeLast(TAIL_KEEP)
        }

        private const val HEAD_KEEP = 8
        private const val TAIL_KEEP = 4
    }
}
