package com.ongo.application.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.video.UploadCompletedEvent
import com.ongo.domain.webhook.WebhookDelivery
import com.ongo.domain.webhook.WebhookDeliveryRepository
import com.ongo.domain.webhook.WebhookRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.Duration
import java.util.UUID
import java.net.InetAddress
import java.net.URI
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Delivers product events through a durable outbox.
 *
 * Upload workers are allowed to finish even when a customer endpoint is down.
 * The persisted delivery is retried with backoff and is visible in the
 * webhook screen, so a transient endpoint failure cannot become a lost event.
 */
@Component
class WebhookDispatcher(
    private val webhookRepository: WebhookRepository,
    private val deliveryRepository: WebhookDeliveryRepository,
    private val objectMapper: ObjectMapper,
    private val allowUnsafeTargets: Boolean = false,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = RestClient.builder()
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(15))
        })
        .build()

    @EventListener
    fun enqueueUploadEvent(event: UploadCompletedEvent) {
        val eventType = if (event.success) "video.published" else "video.failed"
        val eventKey = "video:${event.videoId}:${event.platform.name}:${if (event.success) "published" else "failed"}"
        val payload = runCatching {
            objectMapper.writeValueAsString(
                mapOf(
                    "id" to eventKey,
                    "event" to eventType,
                    "videoId" to event.videoId,
                    "userId" to event.userId,
                    "platform" to event.platform.name,
                    "success" to event.success,
                    "platformUrl" to event.platformUrl,
                    "error" to event.errorMessage,
                    "sentAt" to LocalDateTime.now().toString(),
                )
            )
        }.getOrElse { error ->
            log.error("웹훅 payload 생성 실패. eventKey={}", eventKey, error)
            return
        }

        webhookRepository.findByUserId(event.userId)
            .filter { it.isActive && it.events.contains(eventType) && it.id != null }
            .forEach { webhook ->
                runCatching {
                    deliveryRepository.saveIfAbsent(
                        WebhookDelivery(
                            webhookId = webhook.id!!,
                            eventKey = eventKey,
                            eventType = eventType,
                            payload = payload,
                            nextAttemptAt = LocalDateTime.now(),
                        )
                    )
                }.onSuccess { inserted ->
                    if (!inserted) log.debug("중복 웹훅 이벤트를 건너뜁니다. webhookId={}, eventKey={}", webhook.id, eventKey)
                }.onFailure { error ->
                    log.error("웹훅 outbox 저장 실패. webhookId={}, eventKey={}", webhook.id, eventKey, error)
                }
            }
    }

    @Scheduled(fixedDelayString = "\${webhook.delivery.delay-ms:15000}")
    fun dispatchDueDeliveries() {
        val now = LocalDateTime.now()
        deliveryRepository.findDue(now).forEach { delivery ->
            val claimed = deliveryRepository.claim(
                delivery.id ?: return@forEach,
                "webhook:${UUID.randomUUID()}",
                now,
                now.plusMinutes(2),
            ) ?: return@forEach
            deliver(claimed)
        }
    }

    private fun deliver(delivery: WebhookDelivery) {
        val webhook = webhookRepository.findById(delivery.webhookId)
        val owner = delivery.leaseOwner ?: return
        if (webhook == null || !webhook.isActive) {
            deliveryRepository.updateOwned(delivery.copy(status = "CANCELLED", nextAttemptAt = null), owner)
            return
        }

        runCatching { validateWebhookUrl(webhook.url) }.onFailure { error ->
            deliveryRepository.updateOwned(
                delivery.copy(status = "DEAD_LETTER", nextAttemptAt = null, lastError = error.message),
                owner,
            )
            log.warn("안전하지 않은 웹훅 URL을 차단했습니다. webhookId={}", webhook.id)
            return
        }

        val now = LocalDateTime.now()
        try {
            val signature = sign(webhook.secret, delivery.payload)
            val response = restClient.post()
                .uri(webhook.url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Ongo-Webhook-Id", delivery.id.toString())
                .header("X-Ongo-Webhook-Event", delivery.eventType)
                .header("X-Ongo-Webhook-Signature", signature)
                .body(delivery.payload)
                .exchange { _, clientResponse ->
                    clientResponse.statusCode.value() to (clientResponse.body?.readAllBytes()?.toString(StandardCharsets.UTF_8)?.take(2000))
                }
            val success = response.first in 200..299
            val nextAttempt = if (success || delivery.attemptCount >= MAX_ATTEMPTS) null else now.plusSeconds(backoffSeconds(delivery.attemptCount))
            val status = when {
                success -> "DELIVERED"
                delivery.attemptCount >= MAX_ATTEMPTS -> "DEAD_LETTER"
                else -> "FAILED"
            }
            deliveryRepository.updateOwned(
                delivery.copy(
                    status = status,
                    nextAttemptAt = nextAttempt,
                    statusCode = response.first,
                    responseBody = response.second,
                    sentAt = now,
                    lastError = if (success) null else "웹훅이 HTTP ${response.first} 응답을 반환했습니다.",
                ),
                owner,
            )
            updateWebhookStats(webhook, response.first, success, now)
        } catch (error: Exception) {
            val message = error.message?.take(1000) ?: "웹훅 전송 실패"
            val terminal = delivery.attemptCount >= MAX_ATTEMPTS
            deliveryRepository.updateOwned(
                delivery.copy(
                    status = if (terminal) "DEAD_LETTER" else "FAILED",
                    nextAttemptAt = if (terminal) null else now.plusSeconds(backoffSeconds(delivery.attemptCount)),
                    sentAt = now,
                    lastError = message,
                ),
                owner,
            )
            updateWebhookStats(webhook, null, false, now)
            log.warn("웹훅 전송 실패. webhookId={}, deliveryId={}, attempt={}", webhook.id, delivery.id, delivery.attemptCount, error)
        }
    }

    private fun updateWebhookStats(webhook: com.ongo.domain.webhook.Webhook, statusCode: Int?, success: Boolean, now: LocalDateTime) {
        webhookRepository.update(
            webhook.copy(
                lastTriggeredAt = now,
                lastStatusCode = statusCode,
                failureCount = if (success) 0 else webhook.failureCount + 1,
            )
        )
    }

    private fun sign(secret: String?, payload: String): String {
        if (secret.isNullOrBlank()) return "sha256="
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return "sha256=" + digest.joinToString("") { "%02x".format(it) }
    }

    private fun validateWebhookUrl(value: String) {
        val uri = URI(value)
        if (allowUnsafeTargets) {
            require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true))
            require(uri.userInfo == null && !uri.host.isNullOrBlank())
            return
        }
        require(uri.scheme.equals("https", ignoreCase = true) && uri.userInfo == null && !uri.host.isNullOrBlank())
        val addresses = InetAddress.getAllByName(uri.host)
        require(addresses.none { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress })
    }

    private fun backoffSeconds(attempt: Int): Long =
        (30L * (1L shl (attempt.coerceIn(1, 6) - 1))).coerceAtMost(1800L)

    companion object {
        private const val MAX_ATTEMPTS = 6
    }
}
