package com.ongo.domain.fansegmentcampaign

import java.math.BigDecimal
import java.time.LocalDateTime

data class CampaignSegment(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val criteria: String,
    val fanCount: Int = 0,
    val avgEngagement: BigDecimal = BigDecimal.ZERO,
    val createdAt: LocalDateTime? = null,
)
