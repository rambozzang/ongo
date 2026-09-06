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

        /*
         * **DB 행과 같은 문구를 실어 보낸다.**
         *
         * 프런트는 `payload.title` 이 없으면 `message.type` 으로 폴백한다
         * (`useWebSocket.handleMessage`). 숫자만 보내면 실시간 토스트가 원문 enum 인
         * "CREDIT_LOW" 로 뜨고 본문은 빈 문자열이 된다 — 정작 읽을 수 있는 문구는
         * 바로 위에서 DB 에만 저장된다. 알림센터를 열어야 보이는 것을 실시간이라
         * 부를 수는 없다.
         *
         * 숫자 두 개는 예전 클라이언트를 위해 남긴다.
         */
        webSocketNotificationService.sendToUser(
            userId = event.userId,
            type = "CREDIT_LOW",
            payload = mapOf(
                "title" to notification.title,
                "message" to notification.message,
                "balance" to event.balance,
                "freeMonthly" to event.freeMonthly,
            ),
        )

        log.info("크레딧 부족 알림 전송 완료. userId: {}", event.userId)
    }
}
