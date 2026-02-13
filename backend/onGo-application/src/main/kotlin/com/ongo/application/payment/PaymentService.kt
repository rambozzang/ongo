package com.ongo.application.payment

import com.ongo.application.credit.CreditService
import com.ongo.application.payment.dto.*
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.common.exception.UnauthorizedException
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val creditService: CreditService,
    @Value("\${payment.toss.webhook-secret:}") private val tossWebhookSecret: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getHistory(userId: Long, page: Int, size: Int): PaymentHistoryResponse {
        val totalCount = paymentRepository.countByUserId(userId)
        val payments = paymentRepository.findByUserId(userId, page, size)
        return PaymentHistoryResponse(
            payments = payments.map { it.toItem() },
            totalCount = totalCount,
            page = page,
            size = size
        )
    }

    @Transactional
    fun createPayment(userId: Long, type: PaymentType, amount: Int, description: String): Payment {
        val payment = Payment(
            userId = userId,
            type = type,
            amount = amount,
            status = PaymentStatus.PENDING,
            description = description
        )
        return paymentRepository.save(payment)
    }

    @Transactional
    fun handleWebhook(payload: TossWebhookPayload, signature: String?) {
        // 웹훅 서명 검증
        if (tossWebhookSecret.isNotBlank()) {
            if (signature.isNullOrBlank()) {
                throw UnauthorizedException("웹훅 서명이 누락되었습니다")
            }
            val expectedSignature = computeHmacSha256(payload.orderId + payload.status + payload.totalAmount, tossWebhookSecret)
            if (signature != expectedSignature) {
                throw UnauthorizedException("웹훅 서명 검증에 실패했습니다")
            }
        }

        log.info("결제 웹훅 수신: orderId=${payload.orderId}, status=${payload.status}")
        // orderId에서 paymentId 추출
        val paymentId = payload.orderId.substringAfter("ongo-").toLongOrNull() ?: return

        val payment = paymentRepository.findById(paymentId) ?: return
        val newStatus = when (payload.status) {
            "DONE" -> PaymentStatus.COMPLETED
            "CANCELED" -> PaymentStatus.REFUNDED
            else -> PaymentStatus.FAILED
        }

        paymentRepository.update(payment.copy(
            status = newStatus,
            pgProvider = "toss",
            pgTransactionId = payload.paymentKey,
            paymentMethod = payload.method,
            receiptUrl = payload.receipt?.url
        ))

        if (newStatus == PaymentStatus.COMPLETED) {
            log.info("결제 완료 처리: paymentId=$paymentId, amount=${payload.totalAmount}")
            // 크레딧 구매 결제인 경우 크레딧 지급
            if (payment.type == PaymentType.CREDIT) {
                creditService.addPurchasedCredits(payment.userId, payload.totalAmount, paymentId)
            }
        }
    }

    private fun computeHmacSha256(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray()))
    }

    private fun Payment.toItem(): PaymentItem = PaymentItem(
        id = id!!,
        type = type,
        amount = amount,
        currency = currency,
        status = status,
        description = description,
        receiptUrl = receiptUrl,
        createdAt = createdAt
    )
}
