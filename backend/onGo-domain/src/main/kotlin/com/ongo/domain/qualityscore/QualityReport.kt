package com.ongo.domain.qualityscore

import java.time.LocalDateTime

data class QualityReport(
    val id: Long? = null,
    val userId: Long,
    val videoId: Long? = null,
    val videoTitle: String? = null,
    val overallScore: Int = 0,
    val overallGrade: String = "F",
    val metrics: String = "{}", // JSONB as String
    val improvements: String = "[]", // JSONB as String
    val competitorAvg: Int = 0,
    val checkedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
