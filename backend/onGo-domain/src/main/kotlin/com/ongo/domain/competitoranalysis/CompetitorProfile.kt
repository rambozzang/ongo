package com.ongo.domain.competitoranalysis

import java.math.BigDecimal
import java.time.LocalDateTime

data class CompetitorProfile(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val avatarUrl: String? = null,
    val platforms: String = "[]", // JSONB as String
    val subscriberCount: Long = 0,
    val avgViews: Long = 0,
    val avgEngagement: BigDecimal = BigDecimal.ZERO,
    val addedAt: LocalDateTime? = null,
)

data class CompetitorReport(
    val id: Long? = null,
    val userId: Long,
    val period: String,
    val comparison: String = "{}", // JSONB as String
    val contentGaps: String = "[]", // JSONB as String
    val trendingTopics: String = "[]", // JSONB as String
    val generatedAt: LocalDateTime? = null,
)
