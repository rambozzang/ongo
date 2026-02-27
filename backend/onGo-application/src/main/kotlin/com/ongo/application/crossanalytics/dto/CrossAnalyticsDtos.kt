package com.ongo.application.crossanalytics.dto

import java.time.LocalDateTime

data class CrossPlatformReportResponse(
    val id: Long,
    val period: String,
    val contents: String,
    val platformSummaries: String,
    val audienceOverlap: String,
    val recommendations: String,
    val generatedAt: LocalDateTime?,
)

data class GenerateReportRequest(
    val period: String,
    val contents: String = "[]",
    val platformSummaries: String = "[]",
    val audienceOverlap: String = "{}",
    val recommendations: String = "[]",
)

data class ContentCompareRequest(
    val videoIds: List<Long> = emptyList(),
    val period: String = "30d",
)

data class CrossPlatformContent(
    val videoId: Long,
    val title: String,
    val platform: String,
    val views: Long,
    val likes: Long,
    val comments: Long,
)

data class ContentCompareResponse(
    val contents: List<CrossPlatformContent>,
)
