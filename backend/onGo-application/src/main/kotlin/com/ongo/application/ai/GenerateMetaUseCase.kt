package com.ongo.application.ai

import com.ongo.application.ai.result.MetaGenerationResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GenerateMetaUseCase(
    private val chatClientResolver: ChatClientResolver,
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

        val result = chatClientResolver.resolve(userId).prompt()
            .system(PromptTemplates.META_GENERATION_SYSTEM)
            .user(userPrompt)
            .call()
            .entity(MetaGenerationResult::class.java)

        return result
            ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
    }

    /**
     * **트랜잭션을 열지 않는다.** LLM 호출을 `@Transactional` 안에 두면 `ai_credits` 행
     * 잠금과 DB 커넥션이 모델 응답 시간만큼 묶인다. 차감·환불의 커밋 경계는
     * [CreditService.withCredits] 가 잡는다.
     */
    fun execute(userId: Long, script: String, targetPlatforms: List<Platform>, tone: String, category: String): MetaGenerationResult {
        rateLimiter.checkRateLimit(userId)

        val userPrompt = PromptTemplates.META_GENERATION_USER
            .replace("{script}", InputSanitizer.sanitize(script))
            .replace("{platforms}", targetPlatforms.joinToString(", ") { it.name })
            .replace("{tone}", InputSanitizer.sanitize(tone))
            .replace("{category}", InputSanitizer.sanitize(category))

        return creditService.withCredits(userId, AiFeature.META_GENERATION) {
            try {
                chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.META_GENERATION_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(MetaGenerationResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("AI 메타 생성 실패: userId={}", userId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
            }
        }
    }
}
