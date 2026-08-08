package com.ongo.application.webhook

import com.ongo.application.webhook.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.webhook.Webhook
import com.ongo.domain.webhook.WebhookRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.util.UUID
import java.time.LocalDateTime

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
            isActive = request.isActive ?: webhook.isActive,
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

        val now = LocalDateTime.now()
        return try {
            val response = RestClient.create()
                .post()
                .uri(webhook.url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Ongo-Webhook-Secret", webhook.secret ?: "")
                .body(
                    mapOf(
                        "event" to "webhook.test",
                        "sentAt" to now.toString(),
                        "webhookId" to webhook.id,
                    )
                )
                .exchange { _, clientResponse ->
                    val status = clientResponse.statusCode.value()
                    val body = clientResponse.body?.readAllBytes()?.decodeToString().orEmpty()
                    status to body.take(500)
                }
            val success = response.first in 200..299
            webhookRepository.update(
                webhook.copy(
                    lastTriggeredAt = now,
                    lastStatusCode = response.first,
                    failureCount = if (success) webhook.failureCount else webhook.failureCount + 1,
                )
            )
            WebhookTestResponse(success, response.first, if (success) "테스트 이벤트가 전송되었습니다" else "웹훅이 ${response.first} 응답을 반환했습니다: ${response.second}")
        } catch (e: Exception) {
            webhookRepository.update(
                webhook.copy(lastTriggeredAt = now, lastStatusCode = null, failureCount = webhook.failureCount + 1)
            )
            WebhookTestResponse(false, null, "웹훅 전송 실패: ${e.message ?: "알 수 없는 오류"}")
        }
    }

    @Transactional
    fun rotateSecret(userId: Long, webhookId: Long): WebhookResponse {
        val webhook = webhookRepository.findById(webhookId) ?: throw NotFoundException("웹훅", webhookId)
        if (webhook.userId != userId) throw ForbiddenException("해당 웹훅에 대한 권한이 없습니다")
        return webhookRepository.update(
            webhook.copy(secret = "whsec_${UUID.randomUUID().toString().replace("-", "").take(24)}")
        ).toResponse()
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
