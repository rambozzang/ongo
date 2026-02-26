package com.ongo.domain.teamperformance

import java.math.BigDecimal
import java.time.LocalDateTime

data class TeamMemberPerformance(
    val id: Long = 0,
    val workspaceId: Long,
    val name: String,
    val email: String,
    val role: String,
    val avatar: String? = null,
    val tasksCompleted: Int = 0,
    val tasksAssigned: Int = 0,
    val completionRate: BigDecimal = BigDecimal.ZERO,
    val avgCompletionTime: BigDecimal = BigDecimal.ZERO,
    val contentProduced: Int = 0,
    val onTimeRate: BigDecimal = BigDecimal.ZERO,
    val rating: BigDecimal = BigDecimal.ZERO,
    val streak: Int = 0,
    val lastActiveAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
