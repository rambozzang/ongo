package com.ongo.application.fanreward

import com.ongo.application.fanreward.dto.*
import com.ongo.domain.fanreward.FanActivity
import com.ongo.domain.fanreward.FanActivityRepository
import com.ongo.domain.fanreward.FanReward
import com.ongo.domain.fanreward.FanRewardRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FanRewardUseCase(
    private val rewardRepository: FanRewardRepository,
    private val activityRepository: FanActivityRepository,
) {

    fun getRewards(userId: Long): List<FanRewardResponse> {
        return rewardRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createReward(userId: Long, request: CreateRewardRequest): FanRewardResponse {
        val reward = FanReward(
            userId = userId,
            name = request.name,
            description = request.description,
            pointsCost = request.pointsCost,
            category = request.category,
            maxClaims = request.maxClaims,
        )
        return rewardRepository.save(reward).toResponse()
    }

    fun getActivities(userId: Long): List<FanActivityResponse> {
        return activityRepository.findByUserId(userId).map { it.toResponse() }
    }

    fun getSummary(userId: Long): FanRewardSummaryResponse {
        val rewards = rewardRepository.findByUserId(userId)
        val active = rewards.count { it.isActive }
        val activities = activityRepository.findByUserId(userId)
        return FanRewardSummaryResponse(
            totalRewards = rewards.size,
            activeRewards = active,
            totalPointsDistributed = activities.sumOf { it.points.toLong() },
            totalClaims = rewards.sumOf { it.claimedCount },
            topCategory = rewards.groupBy { it.category }.maxByOrNull { it.value.size }?.key ?: "",
        )
    }

    private fun FanReward.toResponse() = FanRewardResponse(
        id = id!!,
        name = name,
        description = description,
        pointsCost = pointsCost,
        category = category,
        isActive = isActive,
        claimedCount = claimedCount,
        maxClaims = maxClaims,
        imageUrl = imageUrl,
        createdAt = createdAt,
    )

    private fun FanActivity.toResponse() = FanActivityResponse(
        id = id!!,
        fanName = fanName,
        activityType = activityType,
        points = points,
        description = description,
        timestamp = timestamp,
    )
}
