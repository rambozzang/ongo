package com.ongo.application.fansegmentcampaign.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class FanCampaignResponse(
    val id: Long,
    val name: String,
    val segmentId: Long,
    val segmentName: String,
    val campaignType: String,
    val message: String,
    val targetCount: Int,
    val sentCount: Int,
    val openRate: BigDecimal?,
    val clickRate: BigDecimal?,
    val status: String,
    val scheduledAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
)

data class CampaignSegmentResponse(
    val id: Long,
    val name: String,
    val criteria: String,
    val fanCount: Int,
    val avgEngagement: BigDecimal,
    val createdAt: LocalDateTime?,
)

data class FanSegmentCampaignSummaryResponse(
    val totalCampaigns: Int,
    val activeCampaigns: Int,
    val avgOpenRate: BigDecimal,
    val avgClickRate: BigDecimal,
    val totalReach: Int,
)

data class CreateCampaignRequest(
    val name: String,
    val segmentId: Long,
    val campaignType: String,
    val message: String,
)
