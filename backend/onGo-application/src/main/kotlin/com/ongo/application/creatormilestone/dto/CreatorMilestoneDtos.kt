package com.ongo.application.creatormilestone.dto

data class CreatorMilestoneResponse(
    val id: Long,
    val type: String,
    val title: String,
    val description: String,
    val targetValue: Long,
    val currentValue: Long,
    val progress: Double,
    val platform: String,
    val status: String,
    val achievedAt: String?,
    val targetDate: String?,
    val createdAt: String,
)

data class CreateMilestoneRequest(
    val type: String,
    val title: String,
    val description: String,
    val targetValue: Long,
    val platform: String,
    val targetDate: String?,
)

data class CreatorMilestoneSummaryResponse(
    val totalMilestones: Int,
    val achievedCount: Int,
    val inProgressCount: Int,
    val nextMilestone: String,
    val achievementRate: Double,
)
