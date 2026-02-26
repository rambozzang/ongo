package com.ongo.application.platformhealth

import com.ongo.application.platformhealth.dto.*
import com.ongo.domain.platformhealth.*
import org.springframework.stereotype.Service

@Service
class PlatformHealthUseCase(
    private val scoreRepository: PlatformHealthScoreRepository,
    private val issueRepository: HealthIssueRepository,
) {

    fun getScores(workspaceId: Long): List<PlatformHealthScoreResponse> {
        return scoreRepository.findByWorkspaceId(workspaceId).map { toScoreResponse(it) }
    }

    fun getScore(workspaceId: Long, platform: String): PlatformHealthScoreResponse? {
        return scoreRepository.findByWorkspaceIdAndPlatform(workspaceId, platform)?.let { toScoreResponse(it) }
    }

    fun getIssues(healthScoreId: Long): List<HealthIssueResponse> {
        return issueRepository.findByHealthScoreId(healthScoreId).map { toIssueResponse(it) }
    }

    fun getSummary(workspaceId: Long): PlatformHealthSummaryResponse {
        val scores = scoreRepository.findByWorkspaceId(workspaceId)
        val avgScore = if (scores.isNotEmpty()) scores.map { it.overallScore }.average() else 0.0
        val healthiest = scores.maxByOrNull { it.overallScore }?.platform ?: "-"
        val allIssues = scores.flatMap { issueRepository.findByHealthScoreId(it.id) }
        val critical = allIssues.count { it.severity == "HIGH" }
        val trend = scores.groupBy { it.trend }.maxByOrNull { it.value.size }?.key ?: "STABLE"
        return PlatformHealthSummaryResponse(scores.size, avgScore, healthiest, critical, trend)
    }

    private fun toScoreResponse(s: PlatformHealthScore) = PlatformHealthScoreResponse(
        id = s.id, platform = s.platform, overallScore = s.overallScore,
        growthScore = s.growthScore, engagementScore = s.engagementScore,
        consistencyScore = s.consistencyScore, audienceScore = s.audienceScore,
        trend = s.trend, checkedAt = s.checkedAt.toString(),
    )

    private fun toIssueResponse(i: HealthIssue) = HealthIssueResponse(
        id = i.id, healthScoreId = i.healthScoreId, severity = i.severity,
        category = i.category, description = i.description,
        recommendation = i.recommendation,
    )
}
