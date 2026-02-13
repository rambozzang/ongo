package com.ongo.application.ai

import com.ongo.application.ai.result.MetaGenerationResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class GenerateMetaUseCase(
    @Qualifier("anthropicChatClient") private val chatClient: ChatClient,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
) {

    private val log = LoggerFactory.getLogger(GenerateMetaUseCase::class.java)

    fun executeInternal(userId: Long, script: String, targetPlatforms: List<Platform>, tone: String, category: String): MetaGenerationResult {
        val userPrompt = PromptTemplates.META_GENERATION_USER
            .replace("{script}", InputSanitizer.sanitize(script))
            .replace("{platforms}", targetPlatforms.joinToString(", ") { it.name })
            .replace("{tone}", InputSanitizer.sanitize(tone))
            .replace("{category}", InputSanitizer.sanitize(category))

        val result = chatClient.prompt()
            .system(PromptTemplates.META_GENERATION_SYSTEM)
            .user(userPrompt)
            .call()
            .entity(MetaGenerationResult::class.java)

        return result
            ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
    }

    fun execute(userId: Long, script: String, targetPlatforms: List<Platform>, tone: String, category: String): MetaGenerationResult {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.META_GENERATION)

        val userPrompt = PromptTemplates.META_GENERATION_USER
            .replace("{script}", InputSanitizer.sanitize(script))
            .replace("{platforms}", targetPlatforms.joinToString(", ") { it.name })
            .replace("{tone}", InputSanitizer.sanitize(tone))
            .replace("{category}", InputSanitizer.sanitize(category))

        try {
            val result = chatClient.prompt()
                .system(PromptTemplates.META_GENERATION_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(MetaGenerationResult::class.java)

            return result
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("AI 메타 생성 실패, 크레딧 환불 처리: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.META_GENERATION.creditCost, AiFeature.META_GENERATION.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }
}
