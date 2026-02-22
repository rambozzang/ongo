package com.ongo.application.ai

import com.ongo.application.ai.result.BatchReplyDraftResult
import com.ongo.application.ai.result.CommentReplyResult
import com.ongo.application.comment.dto.AiDraftItem
import com.ongo.application.comment.dto.BatchAiDraftResponse
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.comment.CommentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GenerateReplyUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val commentRepository: CommentRepository,
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

    fun executeBatch(userId: Long, commentIds: List<Long>, tone: String): BatchAiDraftResponse {
        rateLimiter.checkRateLimit(userId)

        val comments = commentRepository.findByIds(commentIds)
        if (comments.isEmpty()) {
            throw BusinessException("COMMENTS_NOT_FOUND", "댓글을 찾을 수 없습니다")
        }

        val totalCost = AiFeature.BATCH_REPLY_DRAFT.creditCost * comments.size
        creditService.validateAndDeduct(userId, totalCost, AiFeature.BATCH_REPLY_DRAFT.name)

        val commentsText = comments.mapIndexed { index, comment ->
            "[$index] ${InputSanitizer.sanitize(comment.content)}"
        }.joinToString("\n")

        val toneLabel = when (tone.uppercase()) {
            "FORMAL" -> "정중한"
            "FRIENDLY" -> "친근한"
            "HUMOROUS" -> "유머러스한"
            else -> "친근한"
        }

        val userPrompt = PromptTemplates.BATCH_REPLY_USER
            .replace("{tone}", InputSanitizer.sanitize(toneLabel))
            .replace("{comments}", commentsText)

        try {
            val result = chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.BATCH_REPLY_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(BatchReplyDraftResult::class.java)
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")

            val drafts = result.drafts.mapNotNull { draft ->
                val comment = comments.getOrNull(draft.commentIndex) ?: return@mapNotNull null
                AiDraftItem(
                    commentId = comment.id!!,
                    commentContent = comment.content,
                    draftReply = draft.draftReply,
                    tone = tone,
                )
            }

            return BatchAiDraftResponse(
                drafts = drafts,
                totalCreditsUsed = totalCost,
            )
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("배치 답변 초안 생성 실패, 크레딧 환불 처리: userId={}", userId, e)
            creditService.refundCredit(userId, totalCost, AiFeature.BATCH_REPLY_DRAFT.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }
}
