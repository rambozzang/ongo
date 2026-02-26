package com.ongo.application.faninsights.dto

data class FanDemographicResponse(
    val id: Long,
    val platform: String,
    val ageGroup: String,
    val gender: String,
    val percentage: Double,
    val country: String?,
    val city: String?,
)

data class FanBehaviorResponse(
    val id: Long,
    val platform: String,
    val activeHour: Int,
    val activeDay: String?,
    val watchDuration: Double,
    val returnRate: Double,
    val commentRate: Double,
    val shareRate: Double,
)

data class FanSegmentResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val memberCount: Int,
    val avgEngagement: Double,
    val topInterests: List<String>,
    val platform: String,
)

data class FanInsightsSummaryResponse(
    val totalFans: Long,
    val topAgeGroup: String,
    val topCountry: String,
    val avgWatchDuration: Double,
    val peakActiveHour: Int,
)
