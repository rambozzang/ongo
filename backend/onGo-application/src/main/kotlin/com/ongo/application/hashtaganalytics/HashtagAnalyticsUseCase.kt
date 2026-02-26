package com.ongo.application.hashtaganalytics

import com.ongo.application.hashtaganalytics.dto.*
import com.ongo.domain.hashtaganalytics.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class HashtagAnalyticsUseCase(
    private val performanceRepository: HashtagPerformanceRepository,
    private val groupRepository: HashtagGroupRepository,
) {

    fun getPerformances(workspaceId: Long, platform: String? = null): List<HashtagPerformanceResponse> {
        val list = if (platform != null) performanceRepository.findByWorkspaceIdAndPlatform(workspaceId, platform)
        else performanceRepository.findByWorkspaceId(workspaceId)
        return list.map { toPerformanceResponse(it) }
    }

    fun analyzeHashtags(workspaceId: Long, request: AnalyzeHashtagsRequest): List<HashtagRecommendationResponse> {
        // AI 분석 연동 포인트 — Phase 1에서는 빈 리스트 반환
        return emptyList()
    }

    fun getGroups(workspaceId: Long): List<HashtagGroupResponse> {
        return groupRepository.findByWorkspaceId(workspaceId).map { toGroupResponse(it) }
    }

    @Transactional
    fun createGroup(workspaceId: Long, request: CreateHashtagGroupRequest): HashtagGroupResponse {
        val saved = groupRepository.save(
            HashtagGroup(
                workspaceId = workspaceId,
                name = request.name,
                hashtags = request.hashtags,
                platform = request.platform,
            )
        )
        return toGroupResponse(saved)
    }

    @Transactional
    fun deleteGroup(workspaceId: Long, id: Long) {
        groupRepository.deleteById(id)
    }

    fun getSummary(workspaceId: Long): HashtagAnalyticsSummaryResponse {
        val all = performanceRepository.findByWorkspaceId(workspaceId)
        val trending = all.count { it.trendDirection == "UP" }
        val avgEng = if (all.isNotEmpty()) all.map { it.avgEngagement.toDouble() }.average() else 0.0
        val top = all.maxByOrNull { it.totalViews }?.hashtag ?: "-"
        val groups = groupRepository.findByWorkspaceId(workspaceId)
        return HashtagAnalyticsSummaryResponse(all.size, trending, avgEng, top, groups.size)
    }

    private fun toPerformanceResponse(p: HashtagPerformance) = HashtagPerformanceResponse(
        id = p.id, hashtag = p.hashtag, platform = p.platform, usageCount = p.usageCount,
        totalViews = p.totalViews, avgEngagement = p.avgEngagement.toDouble(),
        growthRate = p.growthRate.toDouble(), trendDirection = p.trendDirection,
        category = p.category, lastUsedAt = p.lastUsedAt?.toString(), createdAt = p.createdAt.toString(),
    )

    private fun toGroupResponse(g: HashtagGroup) = HashtagGroupResponse(
        id = g.id, name = g.name, hashtags = g.hashtags, platform = g.platform,
        usageCount = g.usageCount, createdAt = g.createdAt.toString(),
    )
}
