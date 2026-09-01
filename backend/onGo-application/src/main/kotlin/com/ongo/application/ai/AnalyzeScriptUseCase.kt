package com.ongo.application.ai

import com.ongo.application.ai.result.ScriptAnalysisResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AnalyzeScriptUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
) {

    private val log = LoggerFactory.getLogger(AnalyzeScriptUseCase::class.java)

    fun executeInternal(userId: Long, script: String): ScriptAnalysisResult {
        val sanitizedScript = InputSanitizer.sanitize(script)
        val userPrompt = PromptTemplates.SCRIPT_ANALYSIS_USER
            .replace("{script}", sanitizedScript)

        val result = chatClientResolver.resolve(userId).prompt()
            .system(PromptTemplates.SCRIPT_ANALYSIS_SYSTEM)
            .user(userPrompt)
            .call()
            .entity(ScriptAnalysisResult::class.java)

        return result
            ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
    }

    /**
     * 차감·환불은 [CreditService.withCredits] 한 곳에서 처리한다. `AI_PARSE_ERROR` 도
     * 환불 대상이다.
     *
     * [executeInternal] 은 파이프라인이 이미 예약한 크레딧으로 도는 경로라 차감하지
     * 않는다. 여기서만 과금한다.
     */
    fun execute(userId: Long, script: String): ScriptAnalysisResult {
        rateLimiter.checkRateLimit(userId)

        val sanitizedScript = InputSanitizer.sanitize(script)
        val userPrompt = PromptTemplates.SCRIPT_ANALYSIS_USER
            .replace("{script}", sanitizedScript)

        return creditService.withCredits(userId, AiFeature.SCRIPT_ANALYSIS) {
            try {
                chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.SCRIPT_ANALYSIS_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(ScriptAnalysisResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("AI 스크립트 분석 실패: userId={}", userId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
            }
        }
    }
}
