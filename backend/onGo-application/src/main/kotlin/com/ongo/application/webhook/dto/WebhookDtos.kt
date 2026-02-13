package com.ongo.application.webhook.dto

import java.time.LocalDateTime

data class WebhookResponse(
    val id: Long,
    val name: String,
    val url: String,
    val events: List<String>,
    val secret: String?,
    val isActive: Boolean,
    val lastTriggeredAt: LocalDateTime?,
    val lastStatusCode: Int?,
    val failureCount: Int,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class CreateWebhookRequest(
    val name: String,
    val url: String,
    val events: List<String> = emptyList(),
)

data class UpdateWebhookRequest(
    val name: String? = null,
    val url: String? = null,
    val events: List<String>? = null,
)

data class WebhookTestResponse(
    val success: Boolean,
    val statusCode: Int?,
    val message: String,
)
