package com.ongo.application.ai

import com.ongo.application.ai.result.CommentReplyResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GenerateReplyUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
) {

    private val log = LoggerFactory.getLogger(GenerateReplyUseCase::class.java)

    fun execute(userId: Long, comment: String, channelTone: String, context: String?): CommentReplyResult {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.COMMENT_REPLY)

        val userPrompt = PromptTemplates.COMMENT_REPLY_USER
            .replace("{comment}", InputSanitizer.sanitize(comment))
            .replace("{channelTone}", InputSanitizer.sanitize(channelTone))
            .replace("{context}", InputSanitizer.sanitize(context ?: "없음"))

        try {
            val result = chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.COMMENT_REPLY_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(CommentReplyResult::class.java)

            return result
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("AI 댓글 답변 생성 실패, 크레딧 환불 처리: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.COMMENT_REPLY.creditCost, AiFeature.COMMENT_REPLY.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }
}
