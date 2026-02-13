package com.ongo.application.analytics.dto

data class VideoCompareResponse(
    val videos: List<VideoCompareItem>,
)

data class VideoCompareItem(
    val videoId: Long,
    val title: String?,
    val totalViews: Long,
    val totalLikes: Long,
    val totalComments: Long,
    val totalShares: Long,
    val totalWatchTimeSeconds: Long,
    val avgDailyViews: Long,
    val engagementRate: Double,
)
