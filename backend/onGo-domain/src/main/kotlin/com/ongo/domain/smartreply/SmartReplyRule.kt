package com.ongo.domain.smartreply

import java.time.LocalDateTime

data class SmartReplyRule(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val isActive: Boolean = true,
    val context: String = "COMMENT",
    val triggerKeywords: String = "[]",
    val sentiment: String? = null,
    val tone: String = "FRIENDLY",
    val templateText: String? = null,
    val useAi: Boolean = false,
    val autoSend: Boolean = false,
    val platform: String? = null,
    val replyCount: Int = 0,
    val lastUsed: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
