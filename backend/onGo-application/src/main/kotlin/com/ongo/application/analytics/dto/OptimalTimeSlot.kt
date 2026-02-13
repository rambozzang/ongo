package com.ongo.application.analytics.dto

data class OptimalTimeSlot(
    val dayOfWeek: Int,
    val dayLabel: String,
    val hour: Int,
    val timeLabel: String,
    val expectedViews: Long,
    val engagementRate: Double,
    val confidenceScore: Double,
    val score: Double,
)

data class OptimalTimesResponse(
    val slots: List<OptimalTimeSlot>,
)
