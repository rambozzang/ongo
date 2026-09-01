package com.ongo.application.paddle

import com.ongo.application.webhook.WebhookRetryRunner
import com.ongo.application.webhook.WebhookRetrySpec
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Paddle 웹훅 재시도.
 *
 * 재시도 상태 기계(되살리기·백오프·DEAD_LETTER·PROCESSED 보존)는 [WebhookRetryRunner] 가
 * 포트원과 **공유**한다. 여기서 정하는 것은 무엇을 소유하고 어떻게 재처리하는지뿐이다.
 */
@Component
class WebhookRetryScheduler(
    private val runner: WebhookRetryRunner,
    private val paddleWebhookService: PaddleWebhookService,
) {

    /** PG 마다 달라야 한다. 같으면 한쪽이 도는 동안 다른 쪽이 통째로 스킵된다. */
    private val lockId = javaClass.name.hashCode().toLong()

    /**
     * 1분마다 실패한 웹훅 이벤트 재처리
     */
    @Scheduled(fixedDelay = 60_000)
    fun retryFailedWebhooks() {
        runner.run(
            WebhookRetrySpec(
                provider = "Paddle",
                lockId = lockId,
                eventTypes = PaddleWebhookService.REPROCESSABLE_EVENT_TYPES,
                reprocess = { paddleWebhookService.reprocessWebhookEvent(it) },
            ),
        )
    }
}
