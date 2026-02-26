package com.ongo.application.audienceoverlap.dto

data class AudienceOverlapResultResponse(
    val id: Long,
    val platformA: String,
    val platformB: String,
    val overlapPercent: Double,
    val uniqueToA: Long,
    val uniqueToB: Long,
    val sharedAudience: Long,
    val analyzedAt: String,
)

data class OverlapSegmentResponse(
    val id: Long,
    val name: String,
    val platforms: List<String>,
    val audienceSize: Long,
    val engagementRate: Double,
    val topInterest: String?,
)

data class AnalyzeOverlapRequest(
    val platformA: String,
    val platformB: String,
)

data class AudienceOverlapSummaryResponse(
    val totalAnalyses: Int,
    val avgOverlap: Double,
    val maxOverlapPair: String,
    val totalUniqueAudience: Long,
    val mostSharedSegment: String,
)
