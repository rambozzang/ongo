package com.ongo.application.ugc.dto

import java.time.LocalDateTime

data class ParticipantRewardResponse(
    val participantId: Long,
    val creatorId: Long,
    val agreedReward: Long,
    val rewardId: Long?,
    val baseAmount: Long,
    val bonusAmount: Long,
    val totalAmount: Long,
    val status: String,
    val note: String?,
    val confirmedAt: LocalDateTime?,
)

data class ParticipantRewardListResponse(
    val items: List<ParticipantRewardResponse>,
    val totalBudget: Long,
    val settledTotal: Long,
    val remaining: Long,
)

data class UpdateRewardRequest(
    val baseAmount: Long,
    val bonusAmount: Long = 0,
    val note: String? = null,
)
