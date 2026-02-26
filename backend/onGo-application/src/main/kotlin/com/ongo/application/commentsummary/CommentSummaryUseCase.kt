package com.ongo.application.commentsummary

import com.ongo.application.commentsummary.dto.*
import com.ongo.domain.commentsummary.*
import org.springframework.stereotype.Service

@Service
class CommentSummaryUseCase(
    private val resultRepository: CommentSummaryResultRepository,
    private val topCommentRepository: TopCommentRepository,
) {

    fun getResults(workspaceId: Long, platform: String?): List<CommentSummaryResultResponse> {
        val results = if (platform != null) {
            resultRepository.findByWorkspaceIdAndPlatform(workspaceId, platform)
        } else {
            resultRepository.findByWorkspaceId(workspaceId)
        }
        return results.map { toResultResponse(it) }
    }

    fun analyze(workspaceId: Long, videoId: Long): CommentSummaryResultResponse {
        val result = CommentSummaryResult(
            workspaceId = workspaceId,
            videoTitle = "Video #$videoId",
            platform = "YOUTUBE",
        )
        return toResultResponse(resultRepository.save(result))
    }

    fun getTopComments(summaryId: Long): List<TopCommentResponse> {
        return topCommentRepository.findBySummaryId(summaryId).map { toCommentResponse(it) }
    }

    fun getSummary(workspaceId: Long): CommentSummarySummaryResponse {
        val results = resultRepository.findByWorkspaceId(workspaceId)
        val avgPos = if (results.isNotEmpty()) results.map { it.positivePct }.average() else 0.0
        val avgNeg = if (results.isNotEmpty()) results.map { it.negativePct }.average() else 0.0
        val allTopics = results.flatMap { it.topTopics }
        val topTopic = allTopics.groupBy { it }.maxByOrNull { it.value.size }?.key ?: "-"
        val totalComments = results.sumOf { it.totalComments }
        return CommentSummarySummaryResponse(results.size, avgPos, avgNeg, topTopic, totalComments)
    }

    private fun toResultResponse(r: CommentSummaryResult) = CommentSummaryResultResponse(
        id = r.id, videoTitle = r.videoTitle, platform = r.platform,
        totalComments = r.totalComments, positivePct = r.positivePct,
        negativePct = r.negativePct, neutralPct = r.neutralPct,
        topTopics = r.topTopics, aiSummary = r.aiSummary,
        analyzedAt = r.analyzedAt.toString(),
    )

    private fun toCommentResponse(c: TopComment) = TopCommentResponse(
        id = c.id, summaryId = c.summaryId, author = c.author,
        text = c.text, likes = c.likes, sentiment = c.sentiment,
        isHighlighted = c.isHighlighted,
    )
}
