package com.ongo.domain.channelhealth

import java.time.LocalDateTime

data class ChannelHealthMetric(
    val id: Long? = null,
    val userId: Long,
    val channelId: Long,
    val channelName: String,
    val platform: String,
    val overallScore: Int = 0,
    val growthScore: Int = 0,
    val engagementScore: Int = 0,
    val consistencyScore: Int = 0,
    val audienceScore: Int = 0,
    val monetizationScore: Int = 0,
    val measuredAt: LocalDateTime? = null,
)
