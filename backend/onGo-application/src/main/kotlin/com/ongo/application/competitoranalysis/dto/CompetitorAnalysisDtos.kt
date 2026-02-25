package com.ongo.application.competitoranalysis.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class CompetitorProfileResponse(
    val id: Long,
    val name: String,
    val avatarUrl: String?,
    val platforms: String,
    val subscriberCount: Long,
    val avgViews: Long,
    val avgEngagement: BigDecimal,
    val addedAt: LocalDateTime?,
)

data class CreateCompetitorProfileRequest(
    val name: String,
    val avatarUrl: String? = null,
    val platforms: String = "[]",
    val subscriberCount: Long = 0,
    val avgViews: Long = 0,
    val avgEngagement: BigDecimal = BigDecimal.ZERO,
)

data class CompetitorReportResponse(
    val id: Long,
    val period: String,
    val comparison: String,
    val contentGaps: String,
    val trendingTopics: String,
    val generatedAt: LocalDateTime?,
)

data class CreateCompetitorReportRequest(
    val period: String,
    val comparison: String = "{}",
    val contentGaps: String = "[]",
    val trendingTopics: String = "[]",
)
