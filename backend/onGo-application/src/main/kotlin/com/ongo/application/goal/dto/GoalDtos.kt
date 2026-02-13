package com.ongo.application.goal.dto

import java.time.LocalDate
import java.time.LocalDateTime

data class GoalResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val metricType: String,
    val targetValue: Long,
    val currentValue: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: String,
    val milestones: List<MilestoneResponse>,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class MilestoneResponse(
    val id: Long,
    val title: String,
    val targetValue: Long,
    val isReached: Boolean,
    val reachedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
)

data class CreateGoalRequest(
    val title: String,
    val description: String? = null,
    val metricType: String,
    val targetValue: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

data class UpdateGoalRequest(
    val title: String? = null,
    val description: String? = null,
    val metricType: String? = null,
    val targetValue: Long? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val status: String? = null,
)

data class CreateMilestoneRequest(
    val title: String,
    val targetValue: Long,
)

data class UpdateProgressRequest(
    val currentValue: Long,
)
