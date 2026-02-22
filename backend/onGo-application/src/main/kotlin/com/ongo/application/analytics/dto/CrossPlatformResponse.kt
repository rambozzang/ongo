package com.ongo.application.analytics.dto

data class CrossPlatformComparisonResponse(
    val videoId: Long,
    val videoTitle: String?,
    val platforms: List<PlatformMetrics>,
    val bestPlatform: String?,
    val insights: List<String>,
)

data class PlatformMetrics(
    val platform: String,
    val views: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val watchTimeSeconds: Long,
    val engagementRate: Double,
    val avgViewDuration: Long,
    val revenueMicro: Long,
)

data class CrossPlatformSummaryResponse(
    val videos: List<CrossPlatformComparisonResponse>,
    val platformRankings: Map<String, PlatformRanking>,
)

data class PlatformRanking(
    val platform: String,
    val avgEngagementRate: Double,
    val totalViews: Long,
    val totalRevenue: Long,
    val rank: Int,
)
