package com.ongo.infrastructure.payment

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class PaddleClient(
    private val config: PaddleConfig,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val baseUrl: String
        get() = if (config.environment == "production") "https://api.paddle.com" else "https://sandbox-api.paddle.com"

    private val restClient: RestClient by lazy {
        RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${config.apiKey}")
            .build()
    }

    fun getSubscription(subscriptionId: String): PaddleSubscription {
        log.info("Paddle 구독 조회: subscriptionId=$subscriptionId")
        val json = restClient.get()
            .uri("/subscriptions/$subscriptionId")
            .retrieve()
            .body(String::class.java)
            ?: throw RuntimeException("Paddle 구독 조회 실패: $subscriptionId")
        val wrapper = objectMapper.readValue<Map<String, Any>>(json)
        val dataJson = objectMapper.writeValueAsString(wrapper["data"])
        return objectMapper.readValue(dataJson, PaddleSubscription::class.java)
    }

    fun updateSubscription(subscriptionId: String, newPriceId: String, prorationMode: String = "prorated_immediately"): PaddleSubscription {
        log.info("Paddle 구독 변경: subscriptionId=$subscriptionId, newPriceId=$newPriceId, proration=$prorationMode")
        val body = mapOf(
            "items" to listOf(mapOf("price_id" to newPriceId, "quantity" to 1)),
            "proration_billing_mode" to prorationMode,
        )
        val json = restClient.patch()
            .uri("/subscriptions/$subscriptionId")
            .body(body)
            .retrieve()
            .body(String::class.java)
            ?: throw RuntimeException("Paddle 구독 변경 실패")
        val wrapper = objectMapper.readValue<Map<String, Any>>(json)
        val dataJson = objectMapper.writeValueAsString(wrapper["data"])
        return objectMapper.readValue(dataJson, PaddleSubscription::class.java)
    }

    fun cancelSubscription(subscriptionId: String, effectiveFrom: String = "next_billing_period"): PaddleSubscription {
        log.info("Paddle 구독 취소: subscriptionId=$subscriptionId, effectiveFrom=$effectiveFrom")
        val body = mapOf("effective_from" to effectiveFrom)
        val json = restClient.post()
            .uri("/subscriptions/$subscriptionId/cancel")
            .body(body)
            .retrieve()
            .body(String::class.java)
            ?: throw RuntimeException("Paddle 구독 취소 실패")
        val wrapper = objectMapper.readValue<Map<String, Any>>(json)
        val dataJson = objectMapper.writeValueAsString(wrapper["data"])
        return objectMapper.readValue(dataJson, PaddleSubscription::class.java)
    }

    fun getTransactionInvoice(transactionId: String): String? {
        log.info("Paddle 인보이스 조회: transactionId=$transactionId")
        return try {
            val json = restClient.get()
                .uri("/transactions/$transactionId/invoice")
                .retrieve()
                .body(String::class.java) ?: return null
            val wrapper = objectMapper.readValue<Map<String, Any>>(json)
            val data = wrapper["data"] as? Map<*, *>
            data?.get("url") as? String
        } catch (e: Exception) {
            log.warn("인보이스 조회 실패: transactionId=$transactionId", e)
            null
        }
    }

    fun verifyWebhookSignature(rawBody: String, paddleSignature: String): Boolean {
        if (config.webhookSecret.isBlank()) return false
        try {
            // Paddle signature format: ts=xxx;h1=xxx
            val parts = paddleSignature.split(";").associate {
                val (key, value) = it.split("=", limit = 2)
                key to value
            }
            val ts = parts["ts"] ?: return false
            val h1 = parts["h1"] ?: return false
            val signedPayload = "$ts:$rawBody"
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(config.webhookSecret.toByteArray(), "HmacSHA256"))
            val computed = mac.doFinal(signedPayload.toByteArray()).joinToString("") { "%02x".format(it) }
            return computed == h1
        } catch (e: Exception) {
            log.error("웹훅 서명 검증 오류", e)
            return false
        }
    }

    fun getPriceIdForPlan(planType: String): String? = when (planType) {
        "STARTER" -> config.price.starter.ifBlank { null }
        "PRO" -> config.price.pro.ifBlank { null }
        "BUSINESS" -> config.price.business.ifBlank { null }
        else -> null
    }

    fun getPriceIdForCreditPackage(packageName: String): String? = when (packageName.uppercase()) {
        "STARTER", "AI CREDIT STARTER PACK" -> config.price.creditStarter.ifBlank { null }
        "BASIC", "AI CREDIT BASIC PACK" -> config.price.creditBasic.ifBlank { null }
        "PRO", "AI CREDIT PRO PACK" -> config.price.creditPro.ifBlank { null }
        "BUSINESS", "AI CREDIT BUSINESS PACK" -> config.price.creditBusiness.ifBlank { null }
        else -> null
    }
}
