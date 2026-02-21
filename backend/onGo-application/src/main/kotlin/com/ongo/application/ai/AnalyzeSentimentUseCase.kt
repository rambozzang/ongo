package com.ongo.application.ai

import com.ongo.application.ai.result.SentimentAnalysisResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AnalyzeSentimentUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val rateLimiter: AiRateLimiter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun analyzeBatch(userId: Long, comments: List<String>): List<String> {
        if (comments.isEmpty()) return emptyList()

        rateLimiter.checkRateLimit(userId)

        val numberedComments = comments.mapIndexed { i, c -> "$i: ${c.take(200)}" }.joinToString("\n")
        val userPrompt = PromptTemplates.SENTIMENT_ANALYSIS_USER
            .replace("{comments}", InputSanitizer.sanitize(numberedComments))

        return try {
            val result = chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.SENTIMENT_ANALYSIS_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(SentimentAnalysisResult::class.java)

            if (result != null) {
                val sentimentMap = result.results.associate { it.index to it.sentiment.uppercase() }
                comments.indices.map { sentimentMap[it] ?: "NEUTRAL" }
            } else {
                comments.map { "NEUTRAL" }
            }
        } catch (e: Exception) {
            log.warn("감정 분석 실패, NEUTRAL로 폴백: {}", e.message)
            comments.map { "NEUTRAL" }
        }
    }
}
