package com.ongo.domain.creatormilestone

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class CreatorMilestone(
    val id: Long = 0,
    val workspaceId: Long,
    val type: String,
    val title: String,
    val description: String,
    val targetValue: Long,
    val currentValue: Long = 0,
    val progress: BigDecimal = BigDecimal.ZERO,
    val platform: String,
    val status: String = "PENDING",
    val achievedAt: LocalDateTime? = null,
    val targetDate: LocalDate? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
