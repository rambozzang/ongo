package com.ongo.application.notification

import com.ongo.application.notification.dto.NotificationListResponse
import com.ongo.application.notification.dto.NotificationResponse
import com.ongo.application.notification.dto.UnreadCountResponse
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
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
        val notification = notificationRepository.findById(id)
            ?: throw NotFoundException("알림", id)
        if (notification.userId != userId) {
            throw ForbiddenException("해당 알림에 대한 접근 권한이 없습니다")
        }
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
        val notification = notificationRepository.findById(id)
            ?: throw NotFoundException("알림", id)
        if (notification.userId != userId) {
            throw ForbiddenException("해당 알림에 대한 접근 권한이 없습니다")
        }
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
