package com.ongo.application.audienceoverlap

import com.ongo.application.audienceoverlap.dto.*
import com.ongo.domain.audienceoverlap.*
import org.springframework.stereotype.Service

@Service
class AudienceOverlapUseCase(
    private val resultRepository: AudienceOverlapResultRepository,
    private val segmentRepository: OverlapSegmentRepository,
) {

    fun getResults(workspaceId: Long): List<AudienceOverlapResultResponse> {
        return resultRepository.findByWorkspaceId(workspaceId).map { toResultResponse(it) }
    }

    fun getSegments(workspaceId: Long): List<OverlapSegmentResponse> {
        return segmentRepository.findByWorkspaceId(workspaceId).map { toSegmentResponse(it) }
    }

    fun analyze(workspaceId: Long, request: AnalyzeOverlapRequest): AudienceOverlapResultResponse {
        val result = AudienceOverlapResult(
            workspaceId = workspaceId,
            platformA = request.platformA,
            platformB = request.platformB,
        )
        return toResultResponse(resultRepository.save(result))
    }

    fun getSummary(workspaceId: Long): AudienceOverlapSummaryResponse {
        val results = resultRepository.findByWorkspaceId(workspaceId)
        val avgOverlap = if (results.isNotEmpty()) results.map { it.overlapPercent.toDouble() }.average() else 0.0
        val maxPair = results.maxByOrNull { it.overlapPercent }?.let { "${it.platformA}-${it.platformB}" } ?: "-"
        val totalUnique = results.sumOf { it.uniqueToA + it.uniqueToB + it.sharedAudience }
        val segments = segmentRepository.findByWorkspaceId(workspaceId)
        val topSegment = segments.maxByOrNull { it.audienceSize }?.name ?: "-"
        return AudienceOverlapSummaryResponse(results.size, avgOverlap, maxPair, totalUnique, topSegment)
    }

    private fun toResultResponse(r: AudienceOverlapResult) = AudienceOverlapResultResponse(
        id = r.id, platformA = r.platformA, platformB = r.platformB,
        overlapPercent = r.overlapPercent.toDouble(), uniqueToA = r.uniqueToA,
        uniqueToB = r.uniqueToB, sharedAudience = r.sharedAudience,
        analyzedAt = r.analyzedAt.toString(),
    )

    private fun toSegmentResponse(s: OverlapSegment) = OverlapSegmentResponse(
        id = s.id, name = s.name, platforms = s.platforms,
        audienceSize = s.audienceSize, engagementRate = s.engagementRate.toDouble(),
        topInterest = s.topInterest,
    )
}
