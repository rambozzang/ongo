package com.ongo.application.coupon.dto

import java.time.LocalDateTime

data class ValidateCouponRequest(
    val code: String,
    val targetPlan: String? = null,
    val billingCycle: String? = null,
)

data class CouponValidationResponse(
    val valid: Boolean,
    val code: String,
    val discountType: String? = null,
    val discountValue: Int? = null,
    val calculatedDiscount: Int? = null,
    val message: String? = null,
)

data class ApplyCouponRequest(
    val code: String,
    val subscriptionId: Long? = null,
)

data class CreateCouponRequest(
    val code: String,
    val description: String? = null,
    val discountType: String,
    val discountValue: Int,
    val applicablePlans: String? = null,
    val minBillingCycle: String? = null,
    val maxUses: Int? = null,
    val maxUsesPerUser: Int = 1,
    val validFrom: LocalDateTime = LocalDateTime.now(),
    val validUntil: LocalDateTime? = null,
)

data class CouponResponse(
    val id: Long,
    val code: String,
    val description: String?,
    val discountType: String,
    val discountValue: Int,
    val applicablePlans: String?,
    val maxUses: Int?,
    val usedCount: Int,
    val active: Boolean,
    val validFrom: LocalDateTime,
    val validUntil: LocalDateTime?,
)
