package com.ongo.application.ai.dto

import java.time.LocalDateTime

data class WeeklyDigestResponse(
    val id: Long,
    val weekRange: String,
    val summary: String,
    val topVideos: List<String>,
    val anomalies: List<String>,
    val actionItems: List<String>,
    val generatedAt: LocalDateTime,
)
