package com.ongo.application.teamperformance.dto

data class TeamMemberPerformanceResponse(
    val id: Long,
    val name: String,
    val email: String,
    val role: String,
    val avatar: String?,
    val tasksCompleted: Int,
    val tasksAssigned: Int,
    val completionRate: Double,
    val avgCompletionTime: Double,
    val contentProduced: Int,
    val onTimeRate: Double,
    val rating: Double,
    val streak: Int,
    val lastActiveAt: String?,
)

data class TeamActivityResponse(
    val id: Long,
    val memberId: Long,
    val memberName: String,
    val action: String,
    val target: String,
    val timestamp: String,
)

data class TeamPerformanceSummaryResponse(
    val totalMembers: Int,
    val avgCompletionRate: Double,
    val totalTasksCompleted: Int,
    val totalContentProduced: Int,
    val topPerformer: String,
)
