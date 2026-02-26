package com.ongo.domain.revenuesplit

import java.math.BigDecimal
import java.time.LocalDateTime

data class SplitMember(
    val id: Long = 0,
    val splitId: Long,
    val name: String,
    val email: String? = null,
    val role: String? = null,
    val percentage: BigDecimal,
    val amount: Long = 0,
    val paymentStatus: String = "PENDING",
    val paidAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
