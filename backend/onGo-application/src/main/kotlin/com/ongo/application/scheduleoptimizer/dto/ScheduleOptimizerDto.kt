package com.ongo.application.scheduleoptimizer.dto

import java.time.LocalDateTime

data class OptimalSlotResponse(
    val id: Long,
    val platform: String,
    val dayOfWeek: String,
    val hour: Int,
    val score: Int,
    val audienceOnline: Int,
    val competitionLevel: String,
    val reason: String,
    val createdAt: LocalDateTime?,
)

data class ScheduleRecommendationResponse(
    val id: Long,
    val videoId: Long,
    val videoTitle: String,
    val currentSchedule: LocalDateTime?,
    val recommendedSchedule: LocalDateTime,
    val platform: String,
    val expectedImprovement: Int,
    val confidence: Int,
    val status: String,
    val createdAt: LocalDateTime?,
)

data class ScheduleOptimizerSummaryResponse(
    val totalRecommendations: Int,
    val appliedCount: Int,
    val avgImprovement: Int,
    val bestDay: String,
    val bestHour: Int,
)
