package com.ongo.application.portone

import com.ongo.application.webhook.WebhookRetryRunner
import com.ongo.application.webhook.WebhookRetrySpec
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 포트원 웹훅 재시도.
 *
 * 예전에는 포트원 쪽에 재시도 경로가 **아예 없었다.** 수신 기록이 업무 트랜잭션 안에 있어서
 * 실패하면 행까지 함께 사라졌고, 복구는 전적으로 포트원 자체 재전송에 달려 있었다. 그마저
 * 소진되면 결제·취소가 반영되지 않은 채 흔적 없이 끝났다.
 *
 * 재시도 상태 기계는 [WebhookRetryRunner] 가 Paddle 과 **공유**한다. 여기서 정하는 것은
 * 무엇을 소유하고 어떻게 재처리하는지뿐이다.
 */
@Component
class PortOneWebhookRetryScheduler(
    private val runner: WebhookRetryRunner,
    private val portOnePaymentService: PortOnePaymentService,
) {

    /**
     * **Paddle 스케줄러와 달라야 한다.** 같은 락을 쓰면 한쪽이 도는 동안 다른 쪽이 통째로
     * 스킵된다. 클래스 이름 해시라 자동으로 갈린다.
     */
    private val lockId = javaClass.name.hashCode().toLong()

    @Scheduled(fixedDelay = 60_000)
    fun retryFailedWebhooks() {
        runner.run(
            WebhookRetrySpec(
                provider = "포트원",
                lockId = lockId,
                eventTypes = PortOnePaymentService.REPROCESSABLE_EVENT_TYPES,
                reprocess = { portOnePaymentService.reprocessWebhookEvent(it) },
            ),
        )
    }
}
