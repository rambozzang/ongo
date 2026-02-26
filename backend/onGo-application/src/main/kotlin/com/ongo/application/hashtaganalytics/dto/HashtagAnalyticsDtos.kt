package com.ongo.application.hashtaganalytics.dto

data class HashtagPerformanceResponse(
    val id: Long,
    val hashtag: String,
    val platform: String,
    val usageCount: Int,
    val totalViews: Long,
    val avgEngagement: Double,
    val growthRate: Double,
    val trendDirection: String,
    val category: String?,
    val lastUsedAt: String?,
    val createdAt: String,
)

data class HashtagRecommendationResponse(
    val hashtag: String,
    val relevanceScore: Int,
    val expectedReach: Long,
    val competition: String,
    val reason: String,
)

data class HashtagGroupResponse(
    val id: Long,
    val name: String,
    val hashtags: List<String>,
    val platform: String,
    val usageCount: Int,
    val createdAt: String,
)

data class AnalyzeHashtagsRequest(
    val topic: String,
    val platform: String,
    val count: Int = 10,
)

data class CreateHashtagGroupRequest(
    val name: String,
    val hashtags: List<String>,
    val platform: String,
)

data class HashtagAnalyticsSummaryResponse(
    val totalHashtags: Int,
    val trendingCount: Int,
    val avgEngagementRate: Double,
    val topPerformer: String,
    val groupCount: Int,
)
