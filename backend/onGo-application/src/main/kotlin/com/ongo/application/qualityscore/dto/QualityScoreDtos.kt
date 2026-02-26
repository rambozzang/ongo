package com.ongo.application.qualityscore.dto

import java.time.LocalDateTime

data class QualityCheckRequest(
    val videoId: Long? = null,
    val videoTitle: String,
)

data class QualityCheckResponse(
    val id: Long,
    val videoId: Long?,
    val videoTitle: String?,
    val overallScore: Int,
    val overallGrade: String,
    val metrics: String,
    val improvements: String,
    val competitorAvg: Int,
    val checkedAt: LocalDateTime?,
)

data class QualityReportResponse(
    val id: Long,
    val videoId: Long?,
    val videoTitle: String?,
    val overallScore: Int,
    val overallGrade: String,
    val metrics: String,
    val improvements: String,
    val competitorAvg: Int,
    val checkedAt: LocalDateTime?,
)
