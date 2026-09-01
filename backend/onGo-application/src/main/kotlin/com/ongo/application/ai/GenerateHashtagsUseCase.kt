package com.ongo.application.ai

import com.ongo.application.ai.result.HashtagGenerationResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GenerateHashtagsUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
) {

    private val log = LoggerFactory.getLogger(GenerateHashtagsUseCase::class.java)

    fun executeInternal(userId: Long, title: String, category: String, targetPlatforms: List<Platform>): HashtagGenerationResult {
        val userPrompt = PromptTemplates.HASHTAG_GENERATION_USER
            .replace("{title}", InputSanitizer.sanitize(title))
            .replace("{category}", InputSanitizer.sanitize(category))
            .replace("{platforms}", targetPlatforms.joinToString(", ") { it.name })

        val result = chatClientResolver.resolve(userId).prompt()
            .system(PromptTemplates.HASHTAG_GENERATION_SYSTEM)
            .user(userPrompt)
            .call()
            .entity(HashtagGenerationResult::class.java)

        return result
            ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
    }

    /**
     * **트랜잭션을 열지 않는다.** LLM 호출을 `@Transactional` 안에 두면 `ai_credits` 행
     * 잠금과 DB 커넥션이 모델 응답 시간만큼 묶인다. 차감·환불의 커밋 경계는
     * [CreditService.withCredits] 가 잡는다.
     */
    fun execute(userId: Long, title: String, category: String, targetPlatforms: List<Platform>): HashtagGenerationResult {
        rateLimiter.checkRateLimit(userId)

        val userPrompt = PromptTemplates.HASHTAG_GENERATION_USER
            .replace("{title}", InputSanitizer.sanitize(title))
            .replace("{category}", InputSanitizer.sanitize(category))
            .replace("{platforms}", targetPlatforms.joinToString(", ") { it.name })

        return creditService.withCredits(userId, AiFeature.HASHTAG_RECOMMENDATION) {
            try {
                chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.HASHTAG_GENERATION_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(HashtagGenerationResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("AI 해시태그 생성 실패: userId={}", userId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
            }
        }
    }
}
