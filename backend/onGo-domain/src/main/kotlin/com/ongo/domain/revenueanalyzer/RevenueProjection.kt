package com.ongo.domain.revenueanalyzer

import java.math.BigDecimal
import java.time.LocalDateTime

data class RevenueProjection(
    val id: Long? = null,
    val userId: Long,
    val channelId: Long,
    val source: String,
    val currentMonthly: BigDecimal = BigDecimal.ZERO,
    val projectedMonthly: BigDecimal = BigDecimal.ZERO,
    val confidence: Int = 0,
    val projectionDate: String,
    val factors: List<String> = emptyList(),
    val createdAt: LocalDateTime? = null,
)
