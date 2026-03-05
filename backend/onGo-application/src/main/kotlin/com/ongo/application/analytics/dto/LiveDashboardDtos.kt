package com.ongo.application.analytics.dto

/**
 * 라이브 대시보드 전체 상태 응답.
 * 프론트엔드 LiveDashboardState 타입과 1:1 매핑.
 */
data class LiveDashboardStateResponse(
    val metrics: List<LiveMetricResponse>,
    val alerts: List<LiveAlertResponse>,
    val activePlatforms: List<String>,
    val lastUpdated: String,
    val isConnected: Boolean,
)

/**
 * 개별 지표 카드 데이터.
 * 프론트엔드 LiveMetric 타입과 매핑.
 */
data class LiveMetricResponse(
    val type: String,
    val currentValue: Long,
    val previousValue: Long,
    val changePercent: Double,
    val trend: String,
    val history: List<LiveMetricPointResponse>,
)

data class LiveMetricPointResponse(
    val timestamp: String,
    val value: Long,
)

data class LiveAlertResponse(
    val id: Long,
    val type: String,
    val title: String,
    val description: String,
    val metric: String,
    val value: Long,
    val threshold: Long,
    val createdAt: String?,
    val read: Boolean,
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
