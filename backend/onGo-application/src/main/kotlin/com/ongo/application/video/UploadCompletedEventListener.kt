package com.ongo.application.video

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
class UploadCompletedEventListener(
    private val notificationRepository: NotificationRepository,
    private val webSocketNotificationService: WebSocketNotificationService,
) {

    private val log = LoggerFactory.getLogger(UploadCompletedEventListener::class.java)

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handleUploadCompleted(event: UploadCompletedEvent) {
        val (type, title, message) = if (event.success) {
            Triple(
                NotificationType.UPLOAD_COMPLETE,
                "${event.platform.name} 업로드 완료",
                "영상이 ${event.platform.name}에 성공적으로 업로드되었습니다."
            )
        } else {
            Triple(
                NotificationType.UPLOAD_FAILED,
                "${event.platform.name} 업로드 실패",
                event.errorMessage ?: "업로드 중 오류가 발생했습니다."
            )
        }

        val notification = Notification(
            userId = event.userId,
            type = type,
            title = title,
            message = message,
            referenceType = "video",
            referenceId = event.videoId,
        )
        notificationRepository.save(notification)
        webSocketNotificationService.sendToUser(event.userId, type.name, mapOf("videoId" to event.videoId))

        log.info("업로드 완료 알림 전송. userId: {}, platform: {}, success: {}", event.userId, event.platform, event.success)
    }
}
