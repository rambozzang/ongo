package com.ongo.application.comment

import com.ongo.domain.comment.CrisisDetectionConfig
import com.ongo.domain.comment.CrisisDetectionConfigRepository
import com.ongo.domain.comment.CrisisDetectionResult
import com.ongo.domain.comment.CommentRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class CrisisDetectionUseCase(
    private val commentRepository: CommentRepository,
    private val crisisDetectionConfigRepository: CrisisDetectionConfigRepository,
) {

    fun detect(userId: Long): CrisisDetectionResult {
        val config = crisisDetectionConfigRepository.findByUserId(userId)
            ?: CrisisDetectionConfig(userId = userId)

        if (!config.isEnabled) {
            return CrisisDetectionResult(
                isInCrisis = false,
                currentNegativeCount = 0,
                previousNegativeCount = 0,
                changePercent = 0.0,
                topKeywords = emptyList(),
            )
        }

        val now = LocalDateTime.now()
        val windowStart = now.minusHours(config.windowHours.toLong())
        val prevWindowStart = windowStart.minusHours(config.windowHours.toLong())

        // 현재 구간 부정 댓글 수
        val currentNegative = commentRepository.countByUserIdAndSentimentBetween(
            userId = userId,
            sentiment = "NEGATIVE",
            from = windowStart,
            to = now,
        )

        // 이전 동일 구간 부정 댓글 수
        val previousNegative = commentRepository.countByUserIdAndSentimentBetween(
            userId = userId,
            sentiment = "NEGATIVE",
            from = prevWindowStart,
            to = windowStart,
        )

        val changePercent = if (previousNegative > 0) {
            ((currentNegative - previousNegative).toDouble() / previousNegative) * 100.0
        } else if (currentNegative > 0) {
            100.0
        } else {
            0.0
        }

        val isInCrisis = changePercent >= config.negativeThresholdPct

        // 최근 부정 댓글에서 키워드 추출
        val topKeywords = if (isInCrisis) {
            extractTopKeywords(userId, config.windowHours)
        } else {
            emptyList()
        }

        return CrisisDetectionResult(
            isInCrisis = isInCrisis,
            currentNegativeCount = currentNegative,
            previousNegativeCount = previousNegative,
            changePercent = changePercent,
            topKeywords = topKeywords,
        )
    }

    private fun extractTopKeywords(userId: Long, windowHours: Int): List<String> {
        val comments = commentRepository.findRecentNegativeComments(userId, 50)
        val stopWords = setOf("이", "가", "을", "를", "은", "는", "의", "에", "와", "과", "도", "로", "으로", "에서", "그", "이런", "저런")

        return comments
            .flatMap { comment ->
                comment.content
                    .replace(Regex("[^가-힣a-zA-Z0-9\\s]"), " ")
                    .split("\\s+".toRegex())
                    .filter { it.length >= 2 && it !in stopWords }
            }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }
    }
}
