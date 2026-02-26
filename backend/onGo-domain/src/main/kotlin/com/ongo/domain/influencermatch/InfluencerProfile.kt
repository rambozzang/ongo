package com.ongo.domain.influencermatch

import java.time.LocalDateTime

data class InfluencerProfile(
    val id: Long? = null,
    val userId: Long,
    val channelName: String,
    val platform: String,
    val subscriberCount: Long = 0,
    val avgViews: Long = 0,
    val engagementRate: Double = 0.0,
    val categories: String = "[]", // JSONB as String
    val audienceDemographics: String = "{}", // JSONB as String
    val contactEmail: String? = null,
    val profileUrl: String? = null,
    val matchScore: Int = 0,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
