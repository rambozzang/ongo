package com.ongo.domain.revenuegoal

import java.time.LocalDateTime

data class RevenueGoalMilestone(
    val id: Long = 0,
    val goalId: Long,
    val label: String,
    val targetAmount: Long,
    val reached: Boolean = false,
    val reachedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
