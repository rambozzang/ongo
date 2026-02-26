package com.ongo.application.fanreward.dto

import java.time.LocalDateTime

data class FanRewardResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val pointsCost: Int,
    val category: String,
    val isActive: Boolean,
    val claimedCount: Int,
    val maxClaims: Int?,
    val imageUrl: String?,
    val createdAt: LocalDateTime?,
)

data class FanActivityResponse(
    val id: Long,
    val fanName: String,
    val activityType: String,
    val points: Int,
    val description: String?,
    val timestamp: LocalDateTime?,
)

data class FanRewardSummaryResponse(
    val totalRewards: Int,
    val activeRewards: Int,
    val totalPointsDistributed: Long,
    val totalClaims: Int,
    val topCategory: String,
)

data class CreateRewardRequest(
    val name: String,
    val description: String? = null,
    val pointsCost: Int,
    val category: String = "BADGE",
    val maxClaims: Int? = null,
)
