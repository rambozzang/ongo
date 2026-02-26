package com.ongo.domain.calendarinsights

import java.math.BigDecimal
import java.time.LocalDateTime

data class UploadPattern(
    val id: Long = 0,
    val workspaceId: Long,
    val platform: String,
    val totalUploads: Int = 0,
    val avgUploadsPerWeek: BigDecimal = BigDecimal.ZERO,
    val mostActiveDay: String? = null,
    val mostActiveHour: Int? = null,
    val consistency: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
