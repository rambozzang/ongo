package com.ongo.domain.audiencesegment

import java.time.LocalDateTime

data class AudienceSegment(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val type: String = "CUSTOM",
    val criteria: String = "{}",
    val size: Long = 0,
    val percentage: Double = 0.0,
    val avgWatchTime: Double = 0.0,
    val avgRetention: Double = 0.0,
    val avgCtr: Double = 0.0,
    val avgEngagement: Double = 0.0,
    val growthRate: Double = 0.0,
    val revenueContribution: Double = 0.0,
    val topContentCategories: String = "[]",
    val bestPostingTimes: String = "[]",
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
