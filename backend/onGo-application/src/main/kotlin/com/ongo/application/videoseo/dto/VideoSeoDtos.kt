package com.ongo.application.videoseo.dto

data class SeoAnalysisResponse(
    val id: Long,
    val videoTitle: String,
    val platform: String,
    val overallScore: Int,
    val titleScore: Int,
    val descriptionScore: Int,
    val tagScore: Int,
    val thumbnailScore: Int,
    val suggestions: List<String>,
    val analyzedAt: String,
)

data class SeoKeywordResponse(
    val id: Long,
    val keyword: String,
    val searchVolume: Long,
    val competition: String,
    val relevance: Int,
    val trend: String,
)

data class SeoOptimizeRequest(
    val videoId: Long,
    val platform: String,
)

data class VideoSeoSummaryResponse(
    val totalAnalyzed: Int,
    val avgScore: Double,
    val topKeyword: String,
    val improvementRate: Double,
    val weakestArea: String,
)
