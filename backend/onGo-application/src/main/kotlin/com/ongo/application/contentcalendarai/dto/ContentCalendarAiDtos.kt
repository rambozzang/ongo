package com.ongo.application.contentcalendarai.dto

data class CalendarSuggestionResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val suggestedDate: String,
    val suggestedTime: String,
    val platform: String,
    val contentType: String,
    val topic: String?,
    val expectedEngagement: Double,
    val confidence: Int,
    val status: String,
    val createdAt: String,
)

data class CalendarAiSlotResponse(
    val date: String,
    val time: String,
    val platform: String,
    val score: Int,
    val reason: String?,
)

data class GenerateCalendarRequest(
    val startDate: String,
    val endDate: String,
    val platforms: List<String>,
    val frequency: String,
)

data class ContentCalendarAiSummaryResponse(
    val totalSuggestions: Int,
    val acceptedCount: Int,
    val avgConfidence: Double,
    val bestTimeSlot: String,
    val topPlatform: String,
)
