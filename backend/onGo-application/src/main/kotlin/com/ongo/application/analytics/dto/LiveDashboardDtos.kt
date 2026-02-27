package com.ongo.application.analytics.dto

import java.time.LocalDateTime

data class LiveDashboardStateResponse(
    val totalViewers: Int,
    val activeStreams: Int,
    val peakViewers: Int,
    val avgWatchTime: Int,
    val platforms: List<LivePlatformState>,
)

data class LivePlatformState(
    val platform: String,
    val viewers: Int,
    val isLive: Boolean,
)

data class LiveAlertResponse(
    val id: Long,
    val type: String,
    val message: String,
    val severity: String,
    val isRead: Boolean,
    val createdAt: LocalDateTime?,
)

data class LiveAlertConfigResponse(
    val id: Long,
    val type: String,
    val enabled: Boolean,
    val threshold: Int,
)

data class UpdateLiveAlertConfigRequest(
    val id: Long,
    val type: String,
    val enabled: Boolean,
    val threshold: Int,
)

data class HeatmapRecommendationResponse(
    val dayOfWeek: Int,
    val hour: Int,
    val score: Double,
    val reason: String,
)
