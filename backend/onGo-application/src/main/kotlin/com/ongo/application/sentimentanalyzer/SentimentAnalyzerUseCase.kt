package com.ongo.application.sentimentanalyzer

import com.ongo.application.sentimentanalyzer.dto.*
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.sentimentanalyzer.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class SentimentAnalyzerUseCase(
    private val resultRepository: SentimentResultRepository,
    private val commentRepository: CommentSentimentRepository,
) {

    fun getResults(workspaceId: Long): List<SentimentResultResponse> {
        return resultRepository.findByWorkspaceId(workspaceId).map { toResultResponse(it) }
    }

    /**
     * 개별 분석 결과 상세 조회
     */
    fun getResultById(workspaceId: Long, resultId: Long): SentimentResultResponse {
        val result = resultRepository.findById(resultId)
            ?: throw NotFoundException("감정 분석 결과", resultId)
        if (result.workspaceId != workspaceId) {
            throw com.ongo.common.exception.ForbiddenException("해당 감정 분석 결과에 대한 권한이 없습니다")
        }
        return toResultResponse(result)
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
        // AI 감정 분석 연동 포인트 -- Phase 1에서는 빈 결과 반환
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

    /**
     * 기간별 감정 트렌드 - 시간에 따른 감정 변화
     */
    fun getTrends(workspaceId: Long, days: Int): List<SentimentTrendPoint> {
        val all = resultRepository.findByWorkspaceId(workspaceId)
        val cutoff = LocalDateTime.now().minusDays(days.toLong())

        val filtered = all.filter { it.analyzedAt.isAfter(cutoff) }
        if (filtered.isEmpty()) return emptyList()

        // 날짜별 그룹핑
        val grouped = filtered.groupBy { it.analyzedAt.toLocalDate().toString() }

        return grouped.entries.sortedBy { it.key }.map { (date, results) ->
            val totalComments = results.sumOf { it.totalComments }
            val totalPositive = results.sumOf { it.positiveCount }
            val totalNeutral = results.sumOf { it.neutralCount }
            val totalNegative = results.sumOf { it.negativeCount }
            val total = (totalPositive + totalNeutral + totalNegative).coerceAtLeast(1)

            val avgScore = results.map { it.avgSentimentScore.toDouble() }.average()

            SentimentTrendPoint(
                date = date,
                positiveRate = Math.round(totalPositive.toDouble() / total * 10000) / 100.0,
                neutralRate = Math.round(totalNeutral.toDouble() / total * 10000) / 100.0,
                negativeRate = Math.round(totalNegative.toDouble() / total * 10000) / 100.0,
                avgScore = Math.round(avgScore * 100) / 100.0,
                totalComments = totalComments,
            )
        }
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
