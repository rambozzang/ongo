package com.ongo.api.ai.dto

import java.time.LocalDateTime

data class WeeklyDigestApiResponse(
    val id: Long,
    val weekRange: String,
    val summary: String,
    val topVideos: List<String>,
    val anomalies: List<String>,
    val actionItems: List<String>,
    val generatedAt: LocalDateTime,
)
