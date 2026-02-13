package com.ongo.domain.goal

import java.time.LocalDate
import java.time.LocalDateTime

data class Goal(
    val id: Long? = null,
    val userId: Long,
    val title: String,
    val description: String? = null,
    val metricType: String,
    val targetValue: Long,
    val currentValue: Long = 0,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: String = "ACTIVE",
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)

data class GoalMilestone(
    val id: Long? = null,
    val goalId: Long,
    val title: String,
    val targetValue: Long,
    val isReached: Boolean = false,
    val reachedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
)
