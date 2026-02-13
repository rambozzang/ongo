package com.ongo.domain.webhook

import java.time.LocalDateTime

data class Webhook(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val url: String,
    val events: List<String> = emptyList(),
    val secret: String? = null,
    val isActive: Boolean = true,
    val lastTriggeredAt: LocalDateTime? = null,
    val lastStatusCode: Int? = null,
    val failureCount: Int = 0,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
