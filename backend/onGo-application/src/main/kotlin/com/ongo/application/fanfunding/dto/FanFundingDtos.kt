package com.ongo.application.fanfunding.dto

import java.time.LocalDateTime

data class FundingSummaryResponse(
    val totalRevenue: Long,
    val thisMonthRevenue: Long,
    val lastMonthRevenue: Long,
    val growthRate: Double,
    val topDonors: String,
    val bySource: String,
    val byPlatform: String,
    val dailyTrends: String,
    val membershipCount: Int,
    val membershipMRR: Long,
)

data class FundingTransactionResponse(
    val id: Long,
    val source: String,
    val platform: String,
    val amount: Long,
    val currency: String,
    val donorName: String?,
    val message: String?,
    val videoId: String?,
    val videoTitle: String?,
    val createdAt: LocalDateTime?,
)

data class CreateGoalRequest(
    val title: String,
    val targetAmount: Long,
    val deadline: LocalDateTime? = null,
    val isActive: Boolean = true,
)

data class UpdateGoalRequest(
    val title: String? = null,
    val targetAmount: Long? = null,
    val deadline: LocalDateTime? = null,
    val isActive: Boolean? = null,
)

data class FundingGoalResponse(
    val id: Long,
    val title: String,
    val targetAmount: Long,
    val currentAmount: Long,
    val deadline: LocalDateTime?,
    val isActive: Boolean,
    val createdAt: LocalDateTime?,
)
