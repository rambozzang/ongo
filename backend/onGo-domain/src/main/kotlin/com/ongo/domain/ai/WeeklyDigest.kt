package com.ongo.domain.ai

import java.time.LocalDate
import java.time.LocalDateTime

data class WeeklyDigest(
    val id: Long? = null,
    val userId: Long,
    val weekStartDate: LocalDate,
    val weekEndDate: LocalDate,
    val summary: String,
    val topVideos: String,
    val anomalies: String,
    val actionItems: String,
    val generatedAt: LocalDateTime = LocalDateTime.now(),
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
