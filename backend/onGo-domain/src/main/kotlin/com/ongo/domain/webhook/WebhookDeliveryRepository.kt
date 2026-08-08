package com.ongo.domain.webhook

import java.time.LocalDateTime

interface WebhookDeliveryRepository {
    fun save(delivery: WebhookDelivery): WebhookDelivery
    fun saveIfAbsent(delivery: WebhookDelivery): Boolean
    fun findById(id: Long): WebhookDelivery?
    fun findByWebhookId(webhookId: Long, limit: Int = 50): List<WebhookDelivery>
    fun findDue(now: LocalDateTime, limit: Int = 100): List<WebhookDelivery>
    fun claim(id: Long, owner: String, now: LocalDateTime, leaseUntil: LocalDateTime): WebhookDelivery?
    fun updateOwned(delivery: WebhookDelivery, owner: String): Boolean
    fun requeue(id: Long, now: LocalDateTime): Boolean
}
