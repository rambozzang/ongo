package com.ongo.domain.inbox

import java.time.LocalDateTime

data class InboxMessage(
    val id: Long? = null,
    val userId: Long,
    val platform: String? = null,
    val senderName: String,
    val senderAvatarUrl: String? = null,
    val messageType: String = "COMMENT",
    val content: String,
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    val platformMessageId: String? = null,
    val videoId: Long? = null,
    val receivedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
)
