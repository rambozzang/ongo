package com.ongo.application.inbox.dto

import java.time.LocalDateTime

data class InboxMessageResponse(
    val id: Long,
    val platform: String?,
    val senderName: String,
    val senderAvatarUrl: String?,
    val messageType: String,
    val content: String,
    val isRead: Boolean,
    val isStarred: Boolean,
    val videoId: Long?,
    val receivedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
)

data class InboxListResponse(
    val messages: List<InboxMessageResponse>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
)

data class UnreadCountResponse(
    val count: Int,
)
