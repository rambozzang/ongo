package com.ongo.application.thumbnailabtest.dto

data class ThumbnailVariantResponse(
    val imageUrl: String?,
    val ctr: Double,
    val impressions: Long,
    val clicks: Long,
)

data class ThumbnailTestResponse(
    val id: Long,
    val videoTitle: String,
    val platform: String,
    val status: String,
    val variantA: ThumbnailVariantResponse,
    val variantB: ThumbnailVariantResponse,
    val winner: String?,
    val startedAt: String,
    val endedAt: String?,
)

data class CreateThumbnailTestRequest(
    val videoId: Long,
    val platform: String,
)

data class ThumbnailAbTestSummaryResponse(
    val totalTests: Int,
    val activeTests: Int,
    val avgCtrImprovement: Double,
    val bestVariantWinRate: Double,
    val topPlatform: String,
)
