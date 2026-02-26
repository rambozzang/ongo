package com.ongo.application.influencermatch.dto

import java.time.LocalDateTime

data class FindMatchRequest(
    val categories: List<String> = emptyList(),
    val platform: String? = null,
    val minSubscribers: Long = 0,
    val maxBudget: Long = 0,
)

data class InfluencerMatchResponse(
    val id: Long,
    val channelName: String,
    val platform: String,
    val subscriberCount: Long,
    val avgViews: Long,
    val engagementRate: Double,
    val categories: String,
    val audienceDemographics: String,
    val contactEmail: String?,
    val profileUrl: String?,
    val matchScore: Int,
    val createdAt: LocalDateTime?,
)

data class CreateCollabRequest(
    val influencerProfileId: Long,
    val message: String? = null,
    val proposedBudget: Long = 0,
    val proposedType: String = "COLLABORATION",
)

data class UpdateCollabStatusRequest(
    val status: String,
    val responseMessage: String? = null,
)

data class CollabRequestResponse(
    val id: Long,
    val influencerProfileId: Long,
    val message: String?,
    val proposedBudget: Long,
    val proposedType: String,
    val status: String,
    val responseMessage: String?,
    val respondedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
)
