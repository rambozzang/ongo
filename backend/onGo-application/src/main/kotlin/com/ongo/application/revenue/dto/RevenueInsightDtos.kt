package com.ongo.application.revenue.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class RevenueInsightResponse(
    val id: Long,
    val insightType: String,
    val content: String,
    val platform: String?,
    val confidence: BigDecimal?,
    val createdAt: LocalDateTime?,
)

data class RevenueInsightListResponse(
    val insights: List<RevenueInsightResponse>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
)
