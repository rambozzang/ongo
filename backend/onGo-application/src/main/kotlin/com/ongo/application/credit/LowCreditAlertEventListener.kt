package com.ongo.application.credit

import com.ongo.application.notification.WebSocketNotificationService
import com.ongo.common.enums.NotificationType
import com.ongo.domain.notification.Notification
import com.ongo.domain.notification.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class LowCreditAlertEventListener(
    private val notificationRepository: NotificationRepository,
    private val webSocketNotificationService: WebSocketNotificationService,
) {

    private val log = LoggerFactory.getLogger(LowCreditAlertEventListener::class.java)

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handleLowCreditAlert(event: LowCreditAlertEvent) {
        val notification = Notification(
            userId = event.userId,
            type = NotificationType.CREDIT_LOW,
            title = "AI 크레딧 부족",
            message = "잔여 크레딧이 ${event.balance}/${event.freeMonthly}로 20% 이하입니다. 충전을 권장합니다.",
        )
        notificationRepository.save(notification)

        webSocketNotificationService.sendToUser(
            userId = event.userId,
            type = "CREDIT_LOW",
            payload = mapOf(
                "balance" to event.balance,
                "freeMonthly" to event.freeMonthly,
            ),
        )

        log.info("크레딧 부족 알림 전송 완료. userId: {}", event.userId)
    }
}
