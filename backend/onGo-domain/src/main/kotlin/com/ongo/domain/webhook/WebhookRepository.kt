package com.ongo.domain.webhook

interface WebhookRepository {
    fun findById(id: Long): Webhook?
    fun findByUserId(userId: Long): List<Webhook>
    fun save(webhook: Webhook): Webhook
    fun update(webhook: Webhook): Webhook
    fun delete(id: Long)
}
