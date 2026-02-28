package com.ongo.domain.webhook

import java.time.LocalDateTime

interface WebhookEventRepository {
    fun save(event: WebhookEvent): WebhookEvent
    fun findByEventId(eventId: String): WebhookEvent?
    fun findRetryable(now: LocalDateTime): List<WebhookEvent>
    fun update(event: WebhookEvent)
}
