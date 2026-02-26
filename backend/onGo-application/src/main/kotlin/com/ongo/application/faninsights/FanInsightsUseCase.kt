package com.ongo.application.faninsights

import com.ongo.application.faninsights.dto.*
import com.ongo.domain.faninsights.*
import org.springframework.stereotype.Service

@Service
class FanInsightsUseCase(
    private val demographicRepository: FanDemographicRepository,
    private val behaviorRepository: FanBehaviorRepository,
    private val segmentRepository: FanSegmentRepository,
) {

    fun getDemographics(workspaceId: Long, platform: String? = null): List<FanDemographicResponse> {
        val list = if (platform != null) demographicRepository.findByWorkspaceIdAndPlatform(workspaceId, platform)
        else demographicRepository.findByWorkspaceId(workspaceId)
        return list.map { toDemographicResponse(it) }
    }

    fun getBehaviors(workspaceId: Long, platform: String? = null): List<FanBehaviorResponse> {
        val list = if (platform != null) behaviorRepository.findByWorkspaceIdAndPlatform(workspaceId, platform)
        else behaviorRepository.findByWorkspaceId(workspaceId)
        return list.map { toBehaviorResponse(it) }
    }

    fun getSegments(workspaceId: Long): List<FanSegmentResponse> {
        return segmentRepository.findByWorkspaceId(workspaceId).map { toSegmentResponse(it) }
    }

    fun getSummary(workspaceId: Long): FanInsightsSummaryResponse {
        val demos = demographicRepository.findByWorkspaceId(workspaceId)
        val topAge = demos.groupBy { it.ageGroup }.maxByOrNull { it.value.sumOf { d -> d.percentage.toDouble() } }?.key ?: "-"
        val topCountry = demos.groupBy { it.country }.maxByOrNull { it.value.sumOf { d -> d.percentage.toDouble() } }?.key ?: "-"
        val behaviors = behaviorRepository.findByWorkspaceId(workspaceId)
        val avgWatch = if (behaviors.isNotEmpty()) behaviors.map { it.watchDuration.toDouble() }.average() else 0.0
        val peakHour = behaviors.maxByOrNull { it.returnRate }?.activeHour ?: 0
        val totalFans = demos.sumOf { it.percentage.toLong() * 100 }
        return FanInsightsSummaryResponse(totalFans, topAge, topCountry, avgWatch, peakHour)
    }

    private fun toDemographicResponse(d: FanDemographic) = FanDemographicResponse(
        id = d.id, platform = d.platform, ageGroup = d.ageGroup,
        gender = d.gender, percentage = d.percentage.toDouble(),
        country = d.country, city = d.city,
    )

    private fun toBehaviorResponse(b: FanBehavior) = FanBehaviorResponse(
        id = b.id, platform = b.platform, activeHour = b.activeHour,
        activeDay = b.activeDay, watchDuration = b.watchDuration.toDouble(),
        returnRate = b.returnRate.toDouble(), commentRate = b.commentRate.toDouble(),
        shareRate = b.shareRate.toDouble(),
    )

    private fun toSegmentResponse(s: FanSegment) = FanSegmentResponse(
        id = s.id, name = s.name, description = s.description,
        memberCount = s.memberCount, avgEngagement = s.avgEngagement.toDouble(),
        topInterests = s.topInterests, platform = s.platform,
    )
}
