package com.ongo.domain.payment

import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import java.time.LocalDateTime

data class Payment(
    val id: Long? = null,
    val userId: Long,
    val type: PaymentType,
    val amount: Int,
    val currency: String = "KRW",
    val status: PaymentStatus = PaymentStatus.PENDING,
    val pgProvider: String? = null,
    val pgTransactionId: String? = null,
    val paymentMethod: String? = null,
    val receiptUrl: String? = null,
    val description: String? = null,
    val createdAt: LocalDateTime? = null,
)
