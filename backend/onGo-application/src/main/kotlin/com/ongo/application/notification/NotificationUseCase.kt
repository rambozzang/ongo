package com.ongo.application.notification

import com.ongo.application.notification.dto.NotificationListResponse
import com.ongo.application.notification.dto.NotificationResponse
import com.ongo.application.notification.dto.UnreadCountResponse
import com.ongo.domain.notification.Notification
import com.ongo.domain.notification.NotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationUseCase(
    private val notificationRepository: NotificationRepository,
) {

    fun listNotifications(userId: Long, page: Int, size: Int): NotificationListResponse {
        val notifications = notificationRepository.findByUserId(userId, page, size)
        return NotificationListResponse(
            notifications = notifications.map { it.toResponse() },
            page = page,
            size = size,
        )
    }

    @Transactional
    fun markAsRead(userId: Long, id: Long) {
        notificationRepository.markAsRead(id)
    }

    @Transactional
    fun markAllAsRead(userId: Long) {
        notificationRepository.markAllAsRead(userId)
    }

    fun getUnreadCount(userId: Long): UnreadCountResponse =
        UnreadCountResponse(notificationRepository.countUnreadByUserId(userId))

    @Transactional
    fun deleteNotification(userId: Long, id: Long) {
        // NotificationRepository에 delete 메서드 추가 필요 — 아래에서 직접 구현
        notificationRepository.delete(id)
    }

    private fun Notification.toResponse(): NotificationResponse = NotificationResponse(
        id = id!!,
        type = type.name,
        title = title,
        message = message,
        isRead = isRead,
        referenceType = referenceType,
        referenceId = referenceId,
        createdAt = createdAt,
    )
}
