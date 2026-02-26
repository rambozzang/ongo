package com.ongo.domain.trendpredictor

import java.time.LocalDate
import java.time.LocalDateTime

data class TrendPrediction(
    val id: Long? = null,
    val userId: Long,
    val keyword: String,
    val category: String,
    val platform: String,
    val currentScore: Int = 0,
    val predictedScore: Int = 0,
    val confidence: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    val direction: String = "STABLE",
    val peakDate: LocalDate? = null,
    val createdAt: LocalDateTime? = null,
)
