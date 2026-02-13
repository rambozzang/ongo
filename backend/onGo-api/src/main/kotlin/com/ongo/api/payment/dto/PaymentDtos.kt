package com.ongo.api.payment.dto

import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import java.time.LocalDateTime

data class PaymentItem(
    val id: Long,
    val type: PaymentType,
    val amount: Int,
    val currency: String,
    val status: PaymentStatus,
    val description: String?,
    val receiptUrl: String?,
    val createdAt: LocalDateTime?
)

data class PaymentHistoryResponse(
    val payments: List<PaymentItem>,
    val totalCount: Long,
    val page: Int,
    val size: Int
)

data class TossWebhookPayload(
    val paymentKey: String,
    val orderId: String,
    val status: String,
    val totalAmount: Int,
    val method: String?,
    val approvedAt: String?,
    val receipt: TossReceipt?
)

data class TossReceipt(
    val url: String?
)
