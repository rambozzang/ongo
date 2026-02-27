package com.ongo.application.fansegmentcampaign

import com.ongo.application.fansegmentcampaign.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.fansegmentcampaign.CampaignSegment
import com.ongo.domain.fansegmentcampaign.CampaignSegmentRepository
import com.ongo.domain.fansegmentcampaign.FanCampaign
import com.ongo.domain.fansegmentcampaign.FanCampaignRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class FanSegmentCampaignUseCase(
    private val campaignRepository: FanCampaignRepository,
    private val segmentRepository: CampaignSegmentRepository,
) {

    fun getCampaigns(userId: Long): List<FanCampaignResponse> {
        return campaignRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createCampaign(userId: Long, request: CreateCampaignRequest): FanCampaignResponse {
        val segment = segmentRepository.findById(request.segmentId)
            ?: throw NotFoundException("세그먼트", request.segmentId)
        val campaign = FanCampaign(
            userId = userId,
            name = request.name,
            segmentId = segment.id!!,
            segmentName = segment.name,
            campaignType = request.campaignType,
            message = request.message,
            targetCount = segment.fanCount,
        )
        return campaignRepository.save(campaign).toResponse()
    }

    @Transactional
    fun deleteCampaign(userId: Long, campaignId: Long) {
        val campaign = campaignRepository.findById(campaignId)
            ?: throw NotFoundException("캠페인", campaignId)
        if (campaign.userId != userId) throw ForbiddenException("해당 캠페인에 대한 권한이 없습니다")
        campaignRepository.delete(campaignId)
    }

    fun getSegments(userId: Long): List<CampaignSegmentResponse> {
        return segmentRepository.findByUserId(userId).map { it.toSegmentResponse() }
    }

    fun getSummary(userId: Long): FanSegmentCampaignSummaryResponse {
        val campaigns = campaignRepository.findByUserId(userId)
        val active = campaigns.count { it.status in listOf("SCHEDULED", "SENDING") }
        val completed = campaigns.filter { it.openRate != null }
        val avgOpen = if (completed.isNotEmpty()) {
            completed.mapNotNull { it.openRate }.fold(BigDecimal.ZERO) { acc, v -> acc + v }
                .divide(BigDecimal(completed.size), 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
        val avgClick = if (completed.isNotEmpty()) {
            completed.mapNotNull { it.clickRate }.fold(BigDecimal.ZERO) { acc, v -> acc + v }
                .divide(BigDecimal(completed.size), 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
        val totalReach = campaigns.sumOf { it.sentCount }
        return FanSegmentCampaignSummaryResponse(
            totalCampaigns = campaigns.size,
            activeCampaigns = active,
            avgOpenRate = avgOpen,
            avgClickRate = avgClick,
            totalReach = totalReach,
        )
    }

    private fun FanCampaign.toResponse() = FanCampaignResponse(
        id = id!!, name = name, segmentId = segmentId, segmentName = segmentName,
        campaignType = campaignType, message = message, targetCount = targetCount,
        sentCount = sentCount, openRate = openRate, clickRate = clickRate,
        status = status, scheduledAt = scheduledAt, createdAt = createdAt,
    )

    private fun CampaignSegment.toSegmentResponse() = CampaignSegmentResponse(
        id = id!!, name = name, criteria = criteria,
        fanCount = fanCount, avgEngagement = avgEngagement, createdAt = createdAt,
    )
}
