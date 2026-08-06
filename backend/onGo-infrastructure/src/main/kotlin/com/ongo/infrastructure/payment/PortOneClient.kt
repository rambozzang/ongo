package com.ongo.infrastructure.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.portone.PortOnePayment
import com.ongo.application.portone.PortOnePaymentGateway
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.http.client.SimpleClientHttpRequestFactory
import java.time.Duration

@Component
class PortOneClient(
    private val objectMapper: ObjectMapper,
    private val webhookVerifier: PortOneWebhookVerifier,
    @Value("\${payment.portone.api-secret:}") apiSecret: String,
) : PortOnePaymentGateway {
    private val restClient = RestClient.builder()
        .baseUrl("https://api.portone.io")
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(10))
            setReadTimeout(Duration.ofSeconds(30))
        })
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne $apiSecret")
        .build()

    override fun getPayment(paymentId: String): PortOnePayment {
        val body = restClient.get()
            .uri("/payments/{paymentId}", paymentId)
            .retrieve()
            .body(String::class.java)
            ?: throw IllegalStateException("포트원 결제 조회 응답이 없습니다")
        val json = objectMapper.readTree(body)
        return PortOnePayment(
            paymentId = json.text("paymentId") ?: paymentId,
            status = json.text("status") ?: "UNKNOWN",
            amount = json.path("amount").path("total").asInt(-1),
            currency = json.text("currency") ?: "",
            transactionId = json.text("transactionId")
                ?: json.path("transaction").text("id"),
            paymentMethod = json.path("method").text("type")
                ?: json.path("method").text("name"),
            receiptUrl = json.text("receiptUrl")
                ?: json.path("receipt").text("url"),
        )
    }

    override fun verifyWebhookSignature(
        rawBody: String,
        webhookId: String?,
        webhookSignature: String?,
        webhookTimestamp: String?,
    ): Boolean = webhookVerifier.verify(rawBody, webhookId, webhookSignature, webhookTimestamp)

    private fun JsonNode.text(name: String): String? = path(name).takeUnless { it.isMissingNode || it.isNull }?.asText()
}
