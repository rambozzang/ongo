package com.ongo.domain.webhook

import java.time.LocalDateTime

data class WebhookEvent(
    val id: Long? = null,
    val eventId: String,
    val eventType: String,
    val payload: String,
    val status: String = "PENDING",
    val retryCount: Int = 0,
    val maxRetries: Int = 5,
    val nextRetryAt: LocalDateTime? = null,
    val errorMessage: String? = null,
    val processedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
)
