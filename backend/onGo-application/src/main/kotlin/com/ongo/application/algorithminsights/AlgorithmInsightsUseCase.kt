package com.ongo.application.algorithminsights

import com.ongo.application.algorithminsights.dto.*
import com.ongo.domain.algorithminsights.*
import org.springframework.stereotype.Service

@Service
class AlgorithmInsightsUseCase(
    private val insightRepository: AlgorithmInsightRepository,
    private val scoreRepository: AlgorithmScoreRepository,
    private val changeRepository: AlgorithmChangeRepository,
) {

    fun getInsights(workspaceId: Long, platform: String? = null): List<AlgorithmInsightResponse> {
        val list = if (platform != null) insightRepository.findByWorkspaceIdAndPlatform(workspaceId, platform)
        else insightRepository.findByWorkspaceId(workspaceId)
        return list.map { toInsightResponse(it) }
    }

    fun getScores(workspaceId: Long): List<AlgorithmScoreResponse> {
        return scoreRepository.findByWorkspaceId(workspaceId).map { toScoreResponse(it) }
    }

    fun getScore(workspaceId: Long, platform: String): AlgorithmScoreResponse? {
        return scoreRepository.findByWorkspaceIdAndPlatform(workspaceId, platform)?.let { toScoreResponse(it) }
    }

    fun getChanges(workspaceId: Long): List<AlgorithmChangeResponse> {
        return changeRepository.findByWorkspaceId(workspaceId).map { toChangeResponse(it) }
    }

    fun getSummary(workspaceId: Long): AlgorithmInsightsSummaryResponse {
        val scores = scoreRepository.findByWorkspaceId(workspaceId)
        val avgScore = if (scores.isNotEmpty()) scores.map { it.overallScore.toDouble() }.average() else 0.0
        val best = scores.maxByOrNull { it.overallScore }?.platform ?: "-"
        val insights = insightRepository.findByWorkspaceId(workspaceId)
        val worst = insights.minByOrNull { it.currentScore }?.factor ?: "-"
        val changes = changeRepository.findByWorkspaceId(workspaceId)
        val suggestions = insights.count { it.recommendation != null }
        return AlgorithmInsightsSummaryResponse(avgScore, best, worst, changes.size, suggestions)
    }

    private fun toInsightResponse(i: AlgorithmInsight) = AlgorithmInsightResponse(
        id = i.id, platform = i.platform, factor = i.factor, importance = i.importance,
        currentScore = i.currentScore, recommendation = i.recommendation,
        category = i.category, trend = i.trend, updatedAt = i.updatedAt.toString(),
    )

    private fun toScoreResponse(s: AlgorithmScore) = AlgorithmScoreResponse(
        platform = s.platform, overallScore = s.overallScore, contentScore = s.contentScore,
        engagementScore = s.engagementScore, metadataScore = s.metadataScore,
        consistencyScore = s.consistencyScore, audienceScore = s.audienceScore,
        updatedAt = s.updatedAt.toString(),
    )

    private fun toChangeResponse(c: AlgorithmChange) = AlgorithmChangeResponse(
        id = c.id, platform = c.platform, title = c.title, description = c.description,
        impact = c.impact, affectedAreas = c.affectedAreas, detectedAt = c.detectedAt.toString(),
        recommendation = c.recommendation,
    )
}
