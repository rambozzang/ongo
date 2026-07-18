package com.ongo.application.ugc.dto

import java.time.LocalDateTime

data class PostMetricResponse(
    val campaignPostId: Long,
    val platform: String,
    val postStatus: String,
    val views: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val capturedAt: LocalDateTime?,
)

data class CampaignAnalyticsResponse(
    val campaignId: Long,
    val totalViews: Long,
    val totalLikes: Long,
    val totalComments: Long,
    val totalShares: Long,
    val lastSyncedAt: LocalDateTime?,
    val posts: List<PostMetricResponse>,
)

data class RecordMetricRequest(
    val views: Long = 0,
    val likes: Long = 0,
    val comments: Long = 0,
    val shares: Long = 0,
)
