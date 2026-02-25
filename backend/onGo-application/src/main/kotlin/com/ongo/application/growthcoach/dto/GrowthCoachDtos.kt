package com.ongo.application.growthcoach.dto

import java.time.LocalDateTime

data class GrowthGoalResponse(
    val id: Long,
    val type: String,
    val targetValue: Long,
    val currentValue: Long,
    val deadline: String,
    val progress: Int,
    val createdAt: LocalDateTime?,
)

data class CreateGrowthGoalRequest(
    val type: String,
    val targetValue: Long,
    val deadline: String,
    val currentValue: Long = 0,
    val progress: Int = 0,
)

data class UpdateGrowthGoalRequest(
    val type: String? = null,
    val targetValue: Long? = null,
    val currentValue: Long? = null,
    val deadline: String? = null,
    val progress: Int? = null,
)

data class WeeklyReportResponse(
    val id: Long,
    val weekStart: String,
    val weekEnd: String,
    val summary: String,
    val highlights: String,
    val concerns: String,
    val subscriberGrowth: Int,
    val viewsGrowth: Int,
    val engagementChange: Double,
    val topContent: String,
    val actionItems: String,
    val overallScore: Int,
    val generatedAt: LocalDateTime?,
)
