package com.ongo.application.ai

import com.ongo.application.ai.result.ScriptAnalysisResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class AnalyzeScriptUseCase(
    @Qualifier("anthropicChatClient") private val chatClient: ChatClient,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
) {

    private val log = LoggerFactory.getLogger(AnalyzeScriptUseCase::class.java)

    fun executeInternal(userId: Long, script: String): ScriptAnalysisResult {
        val sanitizedScript = InputSanitizer.sanitize(script)
        val userPrompt = PromptTemplates.SCRIPT_ANALYSIS_USER
            .replace("{script}", sanitizedScript)

        val result = chatClient.prompt()
            .system(PromptTemplates.SCRIPT_ANALYSIS_SYSTEM)
            .user(userPrompt)
            .call()
            .entity(ScriptAnalysisResult::class.java)

        return result
            ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
    }

    fun execute(userId: Long, script: String): ScriptAnalysisResult {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.SCRIPT_ANALYSIS)

        val sanitizedScript = InputSanitizer.sanitize(script)
        val userPrompt = PromptTemplates.SCRIPT_ANALYSIS_USER
            .replace("{script}", sanitizedScript)

        try {
            val result = chatClient.prompt()
                .system(PromptTemplates.SCRIPT_ANALYSIS_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(ScriptAnalysisResult::class.java)

            return result
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("AI 스크립트 분석 실패, 크레딧 환불 처리: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.SCRIPT_ANALYSIS.creditCost, AiFeature.SCRIPT_ANALYSIS.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }
}
