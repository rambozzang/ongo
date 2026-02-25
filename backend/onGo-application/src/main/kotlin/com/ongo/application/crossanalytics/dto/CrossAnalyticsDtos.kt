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
