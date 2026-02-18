package com.ongo.application.inbox

import com.ongo.application.inbox.dto.InboxListResponse
import com.ongo.application.inbox.dto.InboxMessageResponse
import com.ongo.application.inbox.dto.UnreadCountResponse
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.inbox.InboxMessage
import com.ongo.domain.inbox.InboxMessageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InboxUseCase(
    private val inboxMessageRepository: InboxMessageRepository,
) {

    fun listMessages(
        userId: Long,
        page: Int,
        size: Int,
        platform: String?,
        isRead: Boolean?,
        messageType: String?,
    ): InboxListResponse {
        val messages = inboxMessageRepository.findByUserId(userId, page, size, platform, isRead, messageType)
        val total = inboxMessageRepository.countByUserId(userId, platform, isRead, messageType)
        return InboxListResponse(
            messages = messages.map { it.toResponse() },
            totalElements = total,
            page = page,
            size = size,
        )
    }

    @Transactional
    fun markAsRead(userId: Long, id: Long) {
        val message = inboxMessageRepository.findById(id)
            ?: throw NotFoundException("받은 메시지", id)
        if (message.userId != userId) {
            throw ForbiddenException("해당 메시지에 대한 접근 권한이 없습니다")
        }
        inboxMessageRepository.markAsRead(id)
    }

    @Transactional
    fun markAllAsRead(userId: Long) {
        inboxMessageRepository.markAllAsRead(userId)
    }

    @Transactional
    fun toggleStar(userId: Long, id: Long): InboxMessageResponse {
        val message = inboxMessageRepository.findById(id)
            ?: throw NotFoundException("받은 메시지", id)
        if (message.userId != userId) {
            throw ForbiddenException("해당 메시지에 대한 접근 권한이 없습니다")
        }
        inboxMessageRepository.toggleStar(id, !message.isStarred)
        return message.copy(isStarred = !message.isStarred).toResponse()
    }

    fun getUnreadCount(userId: Long): UnreadCountResponse =
        UnreadCountResponse(inboxMessageRepository.countUnreadByUserId(userId))

    private fun InboxMessage.toResponse(): InboxMessageResponse = InboxMessageResponse(
        id = id!!,
        platform = platform,
        senderName = senderName,
        senderAvatarUrl = senderAvatarUrl,
        messageType = messageType,
        content = content,
        isRead = isRead,
        isStarred = isStarred,
        videoId = videoId,
        receivedAt = receivedAt,
        createdAt = createdAt,
    )
}
