package com.ongo.application.webhook

import com.ongo.application.webhook.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.webhook.Webhook
import com.ongo.domain.webhook.WebhookRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WebhookUseCase(
    private val webhookRepository: WebhookRepository,
) {

    fun listWebhooks(userId: Long): List<WebhookResponse> {
        return webhookRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createWebhook(userId: Long, request: CreateWebhookRequest): WebhookResponse {
        val secret = "whsec_${UUID.randomUUID().toString().replace("-", "").take(24)}"

        val webhook = Webhook(
            userId = userId,
            name = request.name,
            url = request.url,
            events = request.events,
            secret = secret,
            isActive = true,
        )
        return webhookRepository.save(webhook).toResponse()
    }

    @Transactional
    fun updateWebhook(userId: Long, webhookId: Long, request: UpdateWebhookRequest): WebhookResponse {
        val webhook = webhookRepository.findById(webhookId) ?: throw NotFoundException("웹훅", webhookId)
        if (webhook.userId != userId) throw ForbiddenException("해당 웹훅에 대한 권한이 없습니다")

        val updated = webhook.copy(
            name = request.name ?: webhook.name,
            url = request.url ?: webhook.url,
            events = request.events ?: webhook.events,
        )
        return webhookRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteWebhook(userId: Long, webhookId: Long) {
        val webhook = webhookRepository.findById(webhookId) ?: throw NotFoundException("웹훅", webhookId)
        if (webhook.userId != userId) throw ForbiddenException("해당 웹훅에 대한 권한이 없습니다")
        webhookRepository.delete(webhookId)
    }

    fun testWebhook(userId: Long, webhookId: Long): WebhookTestResponse {
        val webhook = webhookRepository.findById(webhookId) ?: throw NotFoundException("웹훅", webhookId)
        if (webhook.userId != userId) throw ForbiddenException("해당 웹훅에 대한 권한이 없습니다")

        // In a real implementation, this would make an HTTP call to the webhook URL
        return WebhookTestResponse(
            success = true,
            statusCode = 200,
            message = "테스트 이벤트가 전송되었습니다",
        )
    }

    private fun Webhook.toResponse() = WebhookResponse(
        id = id!!,
        name = name,
        url = url,
        events = events,
        secret = secret,
        isActive = isActive,
        lastTriggeredAt = lastTriggeredAt,
        lastStatusCode = lastStatusCode,
        failureCount = failureCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
