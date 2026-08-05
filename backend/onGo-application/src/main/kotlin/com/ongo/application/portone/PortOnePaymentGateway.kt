package com.ongo.application.portone

/** PortOne 결제 상태를 서버에서 조회하기 위한 애플리케이션 포트. */
interface PortOnePaymentGateway {
    fun getPayment(paymentId: String): PortOnePayment
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
