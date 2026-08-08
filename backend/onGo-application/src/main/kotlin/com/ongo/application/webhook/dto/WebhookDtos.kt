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
    val recentDeliveries: List<WebhookDeliveryResponse> = emptyList(),
)

data class WebhookDeliveryResponse(
    val id: Long,
    val webhookId: Long,
    val eventKey: String,
    val event: String,
    val status: String,
    val statusCode: Int?,
    val responseBody: String?,
    val sentAt: LocalDateTime?,
    val attemptCount: Int,
    val nextAttemptAt: LocalDateTime?,
    val lastError: String?,
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
    val isActive: Boolean? = null,
)

data class WebhookTestResponse(
    val success: Boolean,
    val statusCode: Int?,
    val message: String,
)
