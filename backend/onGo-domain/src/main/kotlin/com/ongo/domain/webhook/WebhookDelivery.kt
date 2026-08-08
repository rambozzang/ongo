package com.ongo.domain.webhook

import java.time.LocalDateTime

/**
 * Outbox row for a user webhook. The payload is persisted before any network
 * call so a restart cannot silently lose a publish notification.
 */
data class WebhookDelivery(
    val id: Long? = null,
    val webhookId: Long,
    val eventKey: String,
    val eventType: String,
    val payload: String,
    val status: String = "PENDING",
    val attemptCount: Int = 0,
    val nextAttemptAt: LocalDateTime? = null,
    val leaseOwner: String? = null,
    val leaseUntil: LocalDateTime? = null,
    val statusCode: Int? = null,
    val responseBody: String? = null,
    val sentAt: LocalDateTime? = null,
    val lastError: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
