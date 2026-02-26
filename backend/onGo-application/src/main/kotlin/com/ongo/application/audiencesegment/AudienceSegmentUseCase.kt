package com.ongo.application.audiencesegment

import com.ongo.application.audiencesegment.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.audiencesegment.AudienceSegment
import com.ongo.domain.audiencesegment.AudienceSegmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AudienceSegmentUseCase(
    private val audienceSegmentRepository: AudienceSegmentRepository,
) {

    fun getAll(userId: Long): List<AudienceSegmentResponse> {
        return audienceSegmentRepository.findByUserId(userId).map { it.toResponse() }
    }

    fun getById(userId: Long, segmentId: Long): AudienceSegmentResponse {
        val segment = audienceSegmentRepository.findById(segmentId)
            ?: throw NotFoundException("오디언스 세그먼트", segmentId)
        if (segment.userId != userId) throw ForbiddenException("해당 세그먼트에 대한 권한이 없습니다")
        return segment.toResponse()
    }

    @Transactional
    fun create(userId: Long, request: CreateSegmentRequest): AudienceSegmentResponse {
        val segment = AudienceSegment(
            userId = userId,
            name = request.name,
            type = request.type,
            criteria = request.criteria,
        )
        return audienceSegmentRepository.save(segment).toResponse()
    }

    @Transactional
    fun delete(userId: Long, segmentId: Long) {
        val segment = audienceSegmentRepository.findById(segmentId)
            ?: throw NotFoundException("오디언스 세그먼트", segmentId)
        if (segment.userId != userId) throw ForbiddenException("해당 세그먼트에 대한 권한이 없습니다")
        audienceSegmentRepository.delete(segmentId)
    }

    fun getInsight(userId: Long, segmentId: Long): SegmentInsightResponse {
        val segment = audienceSegmentRepository.findById(segmentId)
            ?: throw NotFoundException("오디언스 세그먼트", segmentId)
        if (segment.userId != userId) throw ForbiddenException("해당 세그먼트에 대한 권한이 없습니다")
        return SegmentInsightResponse(
            segmentId = segmentId,
            contentRecommendations = "[]",
            titleStyle = "",
            thumbnailStyle = "",
            optimalLength = "{}",
            toneRecommendation = "",
            growthOpportunities = "[]",
        )
    }

    private fun AudienceSegment.toResponse() = AudienceSegmentResponse(
        id = id!!,
        name = name,
        type = type,
        criteria = criteria,
        size = size,
        percentage = percentage,
        avgWatchTime = avgWatchTime,
        avgRetention = avgRetention,
        avgCtr = avgCtr,
        avgEngagement = avgEngagement,
        growthRate = growthRate,
        revenueContribution = revenueContribution,
        topContentCategories = topContentCategories,
        bestPostingTimes = bestPostingTimes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
