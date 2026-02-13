package com.ongo.infrastructure.payment

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.Base64

@Component
class TossPaymentsClient(
    @Value("\${ongo.payment.toss.secret-key:test_sk_placeholder}") private val secretKey: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val baseUrl = "https://api.tosspayments.com/v1"

    private val restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()

    private fun authHeader(): String {
        val encoded = Base64.getEncoder().encodeToString("$secretKey:".toByteArray())
        return "Basic $encoded"
    }

    fun confirmPayment(paymentKey: String, orderId: String, amount: Int): TossPaymentResult {
        log.info("결제 승인 요청: paymentKey=$paymentKey, orderId=$orderId, amount=$amount")
        val response = restClient.post()
            .uri("/payments/confirm")
            .header(HttpHeaders.AUTHORIZATION, authHeader())
            .body(mapOf("paymentKey" to paymentKey, "orderId" to orderId, "amount" to amount))
            .retrieve()
            .body(TossPaymentResult::class.java)
        return response ?: throw RuntimeException("결제 승인 실패")
    }

    fun cancelPayment(paymentKey: String, reason: String): TossPaymentResult {
        log.info("결제 취소 요청: paymentKey=$paymentKey")
        val response = restClient.post()
            .uri("/payments/$paymentKey/cancel")
            .header(HttpHeaders.AUTHORIZATION, authHeader())
            .body(mapOf("cancelReason" to reason))
            .retrieve()
            .body(TossPaymentResult::class.java)
        return response ?: throw RuntimeException("결제 취소 실패")
    }
}

data class TossPaymentResult(
    val paymentKey: String,
    val orderId: String,
    val status: String,
    val totalAmount: Int,
    val method: String?,
    val receipt: TossReceipt?
)

data class TossReceipt(
    val url: String?
)
