package com.ongo.application.revenuegoal.dto

data class RevenueGoalResponse(
    val id: Long,
    val name: String,
    val targetAmount: Long,
    val currentAmount: Long,
    val currency: String,
    val period: String,
    val platform: String,
    val status: String,
    val progress: Int,
    val startDate: String,
    val endDate: String,
)

data class RevenueGoalMilestoneResponse(
    val id: Long,
    val goalId: Long,
    val label: String,
    val targetAmount: Long,
    val reached: Boolean,
    val reachedAt: String?,
)

data class CreateRevenueGoalRequest(
    val name: String,
    val targetAmount: Long,
    val period: String,
    val platform: String = "ALL",
)

data class RevenueGoalSummaryResponse(
    val totalGoals: Int,
    val activeGoals: Int,
    val avgProgress: Double,
    val totalTarget: Long,
    val totalCurrent: Long,
)
