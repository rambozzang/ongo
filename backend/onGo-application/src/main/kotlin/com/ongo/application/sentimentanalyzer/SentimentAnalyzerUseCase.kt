package com.ongo.application.sentimentanalyzer

import com.ongo.application.sentimentanalyzer.dto.*
import com.ongo.domain.sentimentanalyzer.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SentimentAnalyzerUseCase(
    private val resultRepository: SentimentResultRepository,
    private val commentRepository: CommentSentimentRepository,
) {

    fun getResults(workspaceId: Long): List<SentimentResultResponse> {
        return resultRepository.findByWorkspaceId(workspaceId).map { toResultResponse(it) }
    }

    @Transactional
    fun analyze(workspaceId: Long, request: AnalyzeSentimentRequest): SentimentResultResponse {
        val saved = resultRepository.save(
            SentimentResult(
                workspaceId = workspaceId,
                contentTitle = request.contentTitle,
                platform = request.platform,
            )
        )
        // AI 감정 분석 연동 포인트 — Phase 1에서는 빈 결과 반환
        return toResultResponse(saved)
    }

    fun getComments(resultId: Long, sentiment: String? = null): List<CommentSentimentResponse> {
        val comments = if (sentiment != null) commentRepository.findByResultIdAndSentiment(resultId, sentiment)
        else commentRepository.findByResultId(resultId)
        return comments.map { toCommentResponse(it) }
    }

    fun getSummary(workspaceId: Long): SentimentAnalyzerSummaryResponse {
        val all = resultRepository.findByWorkspaceId(workspaceId)
        val avgPositiveRate = if (all.isNotEmpty()) all.map { it.positiveRate.toDouble() }.average() else 0.0
        val avgScore = if (all.isNotEmpty()) all.map { it.avgSentimentScore.toDouble() }.average() else 0.0
        val mostPositive = all.maxByOrNull { it.positiveRate }?.contentTitle ?: "-"
        val mostNegative = all.minByOrNull { it.positiveRate }?.contentTitle ?: "-"
        return SentimentAnalyzerSummaryResponse(all.size, avgPositiveRate, avgScore, mostPositive, mostNegative)
    }

    private fun toResultResponse(r: SentimentResult) = SentimentResultResponse(
        id = r.id, contentTitle = r.contentTitle, platform = r.platform,
        totalComments = r.totalComments, positiveCount = r.positiveCount,
        neutralCount = r.neutralCount, negativeCount = r.negativeCount,
        positiveRate = r.positiveRate.toDouble(), avgSentimentScore = r.avgSentimentScore,
        topPositiveKeywords = r.topPositiveKeywords, topNegativeKeywords = r.topNegativeKeywords,
        analyzedAt = r.analyzedAt.toString(), createdAt = r.createdAt.toString(),
    )

    private fun toCommentResponse(c: CommentSentiment) = CommentSentimentResponse(
        id = c.id, commentText = c.commentText, authorName = c.authorName,
        sentiment = c.sentiment, score = c.score, keywords = c.keywords,
        createdAt = c.createdAt.toString(),
    )
}
