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

    fun compareSegments(userId: Long, request: CompareSegmentsRequest): SegmentComparisonResponse {
        val segments = request.segmentIds.mapNotNull { id ->
            audienceSegmentRepository.findById(id)?.let { segment ->
                if (segment.userId != userId) throw ForbiddenException("해당 세그먼트에 대한 권한이 없습니다")
                segment.toResponse()
            }
        }
        val insights = mutableListOf<String>()
        if (segments.size >= 2) {
            val best = segments.maxByOrNull { it.avgEngagement }
            val worst = segments.minByOrNull { it.avgEngagement }
            if (best != null && worst != null) {
                insights.add("'${best.name}' 세그먼트의 평균 참여율이 가장 높습니다 (${best.avgEngagement})")
                insights.add("'${worst.name}' 세그먼트의 참여율 개선이 필요합니다 (${worst.avgEngagement})")
            }
            val largest = segments.maxByOrNull { it.size }
            if (largest != null) {
                insights.add("'${largest.name}' 세그먼트가 가장 큰 규모입니다 (${largest.size}명)")
            }
        }
        return SegmentComparisonResponse(
            segments = segments,
            insights = insights,
        )
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
