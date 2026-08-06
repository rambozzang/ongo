package com.ongo.application.portone

/** PortOne 결제 상태 조회와 웹훅 서명 검증을 위한 애플리케이션 포트. */
interface PortOnePaymentGateway {
    fun getPayment(paymentId: String): PortOnePayment

    /**
     * Standard Webhooks 규격에 따라 웹훅 서명을 검증한다.
     *
     * @param rawBody 요청 본문 원문 (재직렬화하면 서명이 어긋난다)
     * @param webhookId `webhook-id` 헤더
     * @param webhookSignature `webhook-signature` 헤더
     * @param webhookTimestamp `webhook-timestamp` 헤더
     */
    fun verifyWebhookSignature(
        rawBody: String,
        webhookId: String?,
        webhookSignature: String?,
        webhookTimestamp: String?,
    ): Boolean
}

data class PortOnePayment(
    val paymentId: String,
    val status: String,
    val amount: Int,
    val currency: String,
    val transactionId: String?,
    val paymentMethod: String?,
    val receiptUrl: String?,
)
