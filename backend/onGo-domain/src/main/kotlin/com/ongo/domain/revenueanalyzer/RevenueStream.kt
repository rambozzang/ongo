package com.ongo.domain.revenueanalyzer

import java.math.BigDecimal
import java.time.LocalDateTime

data class RevenueStream(
    val id: Long? = null,
    val userId: Long,
    val channelId: Long,
    val channelName: String,
    val source: String,
    val amount: BigDecimal = BigDecimal.ZERO,
    val currency: String = "KRW",
    val period: String,
    val growth: BigDecimal = BigDecimal.ZERO,
    val createdAt: LocalDateTime? = null,
)
