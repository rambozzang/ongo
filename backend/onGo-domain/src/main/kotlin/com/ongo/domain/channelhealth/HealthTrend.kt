package com.ongo.domain.channelhealth

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class HealthTrend(
    val id: Long? = null,
    val metricId: Long,
    val category: String,
    val trendDate: LocalDate,
    val score: Int = 0,
    val changeValue: BigDecimal = BigDecimal.ZERO,
    val recommendation: String? = null,
    val createdAt: LocalDateTime? = null,
)
