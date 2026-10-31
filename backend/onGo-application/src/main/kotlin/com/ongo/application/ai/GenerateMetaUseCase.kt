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
    /** 내 채널 실측 성과 근거. 없으면(시험 등) 근거 없이 생성한다. */
    private val performanceGrounding: MetaPerformanceGrounding? = null,
) {

    private val log = LoggerFactory.getLogger(GenerateMetaUseCase::class.java)

    /** 생성 결과와, 내 채널 성과를 근거로 썼는지. 화면은 근거 여부를 그대로 보여 준다. */
    data class Outcome(val result: MetaGenerationResult, val grounding: MetaPerformanceGrounding.Grounding)

    private data class Prepared(val system: String, val user: String, val grounding: MetaPerformanceGrounding.Grounding)

    private fun prepare(userId: Long, script: String, targetPlatforms: List<Platform>, tone: String, category: String): Prepared {
        val grounding = performanceGrounding?.forUser(userId)
            ?: MetaPerformanceGrounding.Grounding(null, false, 0, 0, null)
        val user = PromptTemplates.META_GENERATION_USER
            .replace("{script}", InputSanitizer.sanitize(script))
            .replace("{platforms}", targetPlatforms.joinToString(", ") { it.name })
            .replace("{tone}", InputSanitizer.sanitize(tone))
            .replace("{category}", InputSanitizer.sanitize(category))
            .replace("{performance}", grounding.promptBlock ?: "")
        return Prepared(PromptTemplates.metaGenerationSystem(MetaPlatformRules.rulesText(targetPlatforms)), user, grounding)
    }

    fun executeInternal(userId: Long, script: String, targetPlatforms: List<Platform>, tone: String, category: String): MetaGenerationResult {
        val prepared = prepare(userId, script, targetPlatforms, tone, category)
        val result = chatClientResolver.resolve(userId).prompt()
            .system(prepared.system)
            .user(prepared.user)
            .call()
            .entity(MetaGenerationResult::class.java)
            ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
        return MetaPlatformRules.fit(result, targetPlatforms)
    }

    /**
     * **트랜잭션을 열지 않는다.** LLM 호출을 `@Transactional` 안에 두면 `ai_credits` 행
     * 잠금과 DB 커넥션이 모델 응답 시간만큼 묶인다. 차감·환불의 커밋 경계는
     * [CreditService.withCredits] 가 잡는다.
     */
    fun execute(userId: Long, script: String, targetPlatforms: List<Platform>, tone: String, category: String): MetaGenerationResult =
        executeWithGrounding(userId, script, targetPlatforms, tone, category).result

    fun executeWithGrounding(userId: Long, script: String, targetPlatforms: List<Platform>, tone: String, category: String): Outcome {
        rateLimiter.checkRateLimit(userId)
        val prepared = prepare(userId, script, targetPlatforms, tone, category)

        val result = creditService.withCredits(userId, AiFeature.META_GENERATION) {
            try {
                chatClientResolver.resolve(userId).prompt()
                    .system(prepared.system)
                    .user(prepared.user)
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
        // 한도는 게시 때 거절하지 않고 생성 직후 맞춘다.
        return Outcome(MetaPlatformRules.fit(result, targetPlatforms), prepared.grounding)
    }
}
