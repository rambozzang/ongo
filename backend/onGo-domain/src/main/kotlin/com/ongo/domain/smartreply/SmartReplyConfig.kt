package com.ongo.domain.smartreply

import java.time.LocalDateTime

data class SmartReplyConfig(
    val id: Long? = null,
    val userId: Long,
    val defaultTone: String = "FRIENDLY",
    val enableAutoReply: Boolean = false,
    val maxAutoRepliesPerDay: Int = 50,
    val excludeKeywords: String = "[]",
    val replyDelay: Int = 0,
    val platforms: String = "[]",
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
