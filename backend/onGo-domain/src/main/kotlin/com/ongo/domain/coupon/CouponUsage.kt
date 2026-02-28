package com.ongo.domain.coupon

import java.time.LocalDateTime

data class CouponUsage(
    val id: Long? = null,
    val couponId: Long,
    val userId: Long,
    val appliedAt: LocalDateTime = LocalDateTime.now(),
    val discountAmount: Int,
    val subscriptionId: Long? = null,
)
