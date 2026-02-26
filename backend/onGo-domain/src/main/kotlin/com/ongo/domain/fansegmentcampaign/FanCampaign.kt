package com.ongo.domain.fansegmentcampaign

import java.math.BigDecimal
import java.time.LocalDateTime

data class FanCampaign(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val segmentId: Long,
    val segmentName: String,
    val campaignType: String,
    val message: String,
    val targetCount: Int = 0,
    val sentCount: Int = 0,
    val openRate: BigDecimal? = null,
    val clickRate: BigDecimal? = null,
    val status: String = "DRAFT",
    val scheduledAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
)
