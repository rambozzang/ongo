package com.ongo.domain.coupon

import java.time.LocalDateTime

data class Coupon(
    val id: Long? = null,
    val code: String,
    val description: String? = null,
    val discountType: String,
    val discountValue: Int,
    val applicablePlans: String? = null,
    val minBillingCycle: String? = null,
    val maxUses: Int? = null,
    val usedCount: Int = 0,
    val maxUsesPerUser: Int = 1,
    val validFrom: LocalDateTime,
    val validUntil: LocalDateTime? = null,
    val active: Boolean = true,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
