package com.ongo.application.ai

import com.ongo.application.ai.result.ContentIdeaResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GenerateIdeasUseCase(
    @Qualifier("anthropicChatClient") private val chatClient: ChatClient,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
) {

    private val log = LoggerFactory.getLogger(GenerateIdeasUseCase::class.java)

    @Transactional
    fun execute(userId: Long, category: String, recentTitles: List<String>): ContentIdeaResult {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.CONTENT_IDEA)

        val titlesStr = recentTitles.mapIndexed { i, t -> "${i + 1}. ${InputSanitizer.sanitize(t)}" }.joinToString("\n")
        val userPrompt = PromptTemplates.CONTENT_IDEA_USER
            .replace("{category}", InputSanitizer.sanitize(category))
            .replace("{recentTitles}", titlesStr)

        try {
            val result = chatClient.prompt()
                .system(PromptTemplates.CONTENT_IDEA_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(ContentIdeaResult::class.java)

            return result
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("AI 아이디어 생성 실패, 크레딧 환불 처리: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.CONTENT_IDEA.creditCost, AiFeature.CONTENT_IDEA.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }
}
