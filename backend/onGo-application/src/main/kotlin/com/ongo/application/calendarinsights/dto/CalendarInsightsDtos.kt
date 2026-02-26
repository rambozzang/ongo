package com.ongo.application.calendarinsights.dto

data class CalendarInsightResponse(
    val id: Long,
    val date: String,
    val dayOfWeek: String,
    val hour: Int,
    val uploadCount: Int,
    val avgViews: Long,
    val avgEngagement: Double,
    val score: Int,
)

data class OptimalTimeSlotResponse(
    val dayOfWeek: String,
    val hour: Int,
    val score: Int,
    val expectedViews: Long,
    val expectedEngagement: Double,
    val reason: String?,
)

data class UploadPatternResponse(
    val platform: String,
    val totalUploads: Int,
    val avgUploadsPerWeek: Double,
    val mostActiveDay: String?,
    val mostActiveHour: Int?,
    val consistency: Int,
)

data class CalendarInsightsSummaryResponse(
    val totalUploads: Int,
    val avgUploadsPerWeek: Double,
    val bestDay: String,
    val bestHour: Int,
    val consistencyScore: Int,
)
