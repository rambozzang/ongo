package com.ongo.application.ai

import com.ongo.application.ai.result.HashtagGenerationResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class GenerateHashtagsUseCase(
    @Qualifier("anthropicChatClient") private val chatClient: ChatClient,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
) {

    private val log = LoggerFactory.getLogger(GenerateHashtagsUseCase::class.java)

    fun executeInternal(userId: Long, title: String, category: String, targetPlatforms: List<Platform>): HashtagGenerationResult {
        val userPrompt = PromptTemplates.HASHTAG_GENERATION_USER
            .replace("{title}", InputSanitizer.sanitize(title))
            .replace("{category}", InputSanitizer.sanitize(category))
            .replace("{platforms}", targetPlatforms.joinToString(", ") { it.name })

        val result = chatClient.prompt()
            .system(PromptTemplates.HASHTAG_GENERATION_SYSTEM)
            .user(userPrompt)
            .call()
            .entity(HashtagGenerationResult::class.java)

        return result
            ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
    }

    fun execute(userId: Long, title: String, category: String, targetPlatforms: List<Platform>): HashtagGenerationResult {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.HASHTAG_RECOMMENDATION)

        val userPrompt = PromptTemplates.HASHTAG_GENERATION_USER
            .replace("{title}", InputSanitizer.sanitize(title))
            .replace("{category}", InputSanitizer.sanitize(category))
            .replace("{platforms}", targetPlatforms.joinToString(", ") { it.name })

        try {
            val result = chatClient.prompt()
                .system(PromptTemplates.HASHTAG_GENERATION_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(HashtagGenerationResult::class.java)

            return result
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("AI 해시태그 생성 실패, 크레딧 환불 처리: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.HASHTAG_RECOMMENDATION.creditCost, AiFeature.HASHTAG_RECOMMENDATION.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }
}
