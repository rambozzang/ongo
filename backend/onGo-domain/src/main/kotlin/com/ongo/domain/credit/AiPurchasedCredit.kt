package com.ongo.domain.credit

import java.time.LocalDateTime

data class AiPurchasedCredit(
    val id: Long? = null,
    val userId: Long,
    val packageName: String,
    val totalCredits: Int,
    val remaining: Int,
    val price: Int,
    val purchasedAt: LocalDateTime? = null,
    val expiresAt: LocalDateTime,
    val status: String = "ACTIVE",
)
