package com.ongo.domain.growthcoach

import java.time.LocalDateTime

data class WeeklyReport(
    val id: Long? = null,
    val userId: Long,
    val weekStart: String,
    val weekEnd: String,
    val summary: String,
    val highlights: String = "[]", // JSONB as String
    val concerns: String = "[]", // JSONB as String
    val subscriberGrowth: Int = 0,
    val viewsGrowth: Int = 0,
    val engagementChange: Double = 0.0,
    val topContent: String = "[]", // JSONB as String
    val actionItems: String = "[]", // JSONB as String
    val overallScore: Int = 0,
    val generatedAt: LocalDateTime? = null,
)
