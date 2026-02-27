package com.ongo.application.audiencesegment.dto

import java.time.LocalDateTime

data class CreateSegmentRequest(
    val name: String,
    val type: String = "CUSTOM",
    val criteria: String = "{}",
)

data class AudienceSegmentResponse(
    val id: Long,
    val name: String,
    val type: String,
    val criteria: String,
    val size: Long,
    val percentage: Double,
    val avgWatchTime: Double,
    val avgRetention: Double,
    val avgCtr: Double,
    val avgEngagement: Double,
    val growthRate: Double,
    val revenueContribution: Double,
    val topContentCategories: String,
    val bestPostingTimes: String,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class SegmentInsightResponse(
    val segmentId: Long,
    val contentRecommendations: String,
    val titleStyle: String,
    val thumbnailStyle: String,
    val optimalLength: String,
    val toneRecommendation: String,
    val growthOpportunities: String,
)

data class CompareSegmentsRequest(
    val segmentIds: List<Long>,
)

data class SegmentComparisonResponse(
    val segments: List<AudienceSegmentResponse>,
    val insights: List<String>,
)
