package com.ongo.domain.crossanalytics

import java.time.LocalDateTime

data class CrossPlatformReport(
    val id: Long? = null,
    val userId: Long,
    val period: String,
    val contents: String = "[]", // JSONB as String
    val platformSummaries: String = "[]", // JSONB as String
    val audienceOverlap: String = "{}", // JSONB as String
    val recommendations: String = "[]", // JSONB as String
    val generatedAt: LocalDateTime? = null,
)
