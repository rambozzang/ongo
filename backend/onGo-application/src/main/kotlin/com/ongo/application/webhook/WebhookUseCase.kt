package com.ongo.application.webhook

import com.ongo.application.webhook.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.webhook.Webhook
import com.ongo.domain.webhook.WebhookRepository
import com.ongo.domain.webhook.WebhookDeliveryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.util.UUID
import java.time.LocalDateTime
import java.time.Duration
import java.net.InetAddress
import java.net.URI

@Service
class WebhookUseCase(
    private val webhookRepository: WebhookRepository,
    private val deliveryRepository: WebhookDeliveryRepository,
) {

    fun listWebhooks(userId: Long): List<WebhookResponse> {
        return webhookRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createWebhook(userId: Long, request: CreateWebhookRequest): WebhookResponse {
        validateWebhookUrl(request.url)
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
        request.url?.let(::validateWebhookUrl)

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
        validateWebhookUrl(webhook.url)

        val now = LocalDateTime.now()
        return try {
            val response = RestClient.builder()
                .requestFactory(SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(5))
                    setReadTimeout(Duration.ofSeconds(15))
                })
                .build()
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
    fun retryDelivery(userId: Long, webhookId: Long, deliveryId: Long): WebhookDeliveryResponse {
        val webhook = webhookRepository.findById(webhookId) ?: throw NotFoundException("웹훅", webhookId)
        if (webhook.userId != userId) throw ForbiddenException("해당 웹훅에 대한 권한이 없습니다")
        val delivery = deliveryRepository.findById(deliveryId)
            ?: throw NotFoundException("웹훅 배달", deliveryId)
        if (delivery.webhookId != webhookId) throw ForbiddenException("해당 웹훅 배달에 대한 권한이 없습니다")
        if (!deliveryRepository.requeue(deliveryId, LocalDateTime.now())) {
            throw IllegalStateException("웹훅 배달을 재시도 대기열에 넣지 못했습니다")
        }
        return deliveryRepository.findById(deliveryId)!!.toResponse()
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
        recentDeliveries = id?.let { deliveryRepository.findByWebhookId(it).map { delivery -> delivery.toResponse() } } ?: emptyList(),
    )

    fun com.ongo.domain.webhook.WebhookDelivery.toResponse() = WebhookDeliveryResponse(
        id = id!!,
        webhookId = webhookId,
        eventKey = eventKey,
        event = eventType,
        status = status,
        statusCode = statusCode,
        responseBody = responseBody ?: lastError,
        sentAt = sentAt ?: createdAt,
        attemptCount = attemptCount,
        nextAttemptAt = nextAttemptAt,
        lastError = lastError,
    )

    /** Webhook URLs are server-side request targets; reject obvious SSRF targets. */
    private fun validateWebhookUrl(value: String) {
        val uri = runCatching { URI(value) }
            .getOrElse { throw IllegalArgumentException("웹훅 URL 형식이 올바르지 않습니다") }
        require(uri.scheme.equals("https", ignoreCase = true)) { "웹훅 URL은 HTTPS여야 합니다" }
        require(uri.userInfo == null && !uri.host.isNullOrBlank()) { "웹훅 URL에 사용자 정보 또는 호스트가 없습니다" }
        val addresses = runCatching { InetAddress.getAllByName(uri.host) }
            .getOrElse { throw IllegalArgumentException("웹훅 호스트를 확인할 수 없습니다") }
        require(addresses.isNotEmpty() && addresses.none { address ->
            address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress
        }) { "웹훅 URL은 내부 네트워크 주소를 사용할 수 없습니다" }
    }
}
