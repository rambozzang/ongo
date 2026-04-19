package com.ongo.domain.comment

import java.time.LocalDateTime

data class CrisisDetectionConfig(
    val id: Long? = null,
    val userId: Long,
    val negativeThresholdPct: Int = 300,
    val windowHours: Int = 24,
    val isEnabled: Boolean = true,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
