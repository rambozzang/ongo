package com.ongo.api.analytics.dto

import com.ongo.common.enums.Platform
import java.time.LocalDate

data class DashboardKpiResponse(
    val totalViews: Long,
    val viewsChangePercent: Double,
    val totalSubscribers: Long,
    val subscribersChange: Long,
    val totalLikes: Long,
    val likesChangePercent: Double,
    val creditBalance: Int,
    val creditTotal: Int
)

data class TrendPoint(
    val date: LocalDate,
    val totalViews: Long,
    val platformViews: Map<String, Long>
)

data class TrendDataResponse(
    val data: List<TrendPoint>
)

data class PlatformAnalyticsDetail(
    val platform: Platform,
    val views: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val dailyData: List<DailyMetric>
)

data class DailyMetric(
    val date: LocalDate,
    val views: Int,
    val likes: Int,
    val comments: Int
)

data class VideoAnalyticsResponse(
    val videoId: Long,
    val title: String?,
    val platforms: List<PlatformAnalyticsDetail>
)

data class HeatmapResponse(
    val data: Map<String, Map<Int, Long>>
)

data class TopVideoItem(
    val id: Long,
    val title: String,
    val thumbnailUrl: String?,
    val totalViews: Long,
    val platforms: List<String>
)

data class TopVideoResponse(
    val videos: List<TopVideoItem>
)

data class PlatformSummary(
    val platform: Platform,
    val views: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long
)

data class PlatformComparisonResponse(
    val platforms: List<PlatformSummary>
)
