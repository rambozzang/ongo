package com.ongo.application.ai

import com.ongo.application.ai.result.BatchReplyDraftResult
import com.ongo.application.ai.result.CommentReplyResult
import com.ongo.application.comment.dto.AiDraftItem
import com.ongo.application.comment.dto.BatchAiDraftResponse
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
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

    /**
     * 차감·환불은 [CreditService.withCredits] 한 곳에서 처리한다. `AI_PARSE_ERROR` 도
     * 환불 대상이다 — 예전에는 그 분기가 환불 없이 그대로 올라갔다.
     */
    fun execute(userId: Long, comment: String, channelTone: String, context: String?): CommentReplyResult {
        rateLimiter.checkRateLimit(userId)

        val userPrompt = PromptTemplates.COMMENT_REPLY_USER
            .replace("{comment}", InputSanitizer.sanitize(comment))
            .replace("{channelTone}", InputSanitizer.sanitize(channelTone))
            .replace("{context}", InputSanitizer.sanitize(context ?: "없음"))

        return creditService.withCredits(userId, AiFeature.COMMENT_REPLY) {
            try {
                chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.COMMENT_REPLY_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(CommentReplyResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("AI 댓글 답변 생성 실패: userId={}", userId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
            }
        }
    }

    /**
     * 선택한 댓글들의 답변 초안을 한 번에 만든다.
     *
     * - **본인 댓글만** 다룬다. 예전에는 ID 목록을 그대로 읽어, 남의 댓글 ID 를 넣으면 그 내용과 초안이 응답에 실렸다.
     * - 한 번에 [MAX_BATCH_DRAFTS] 개까지. 모델 입력이 커지면 원가 예산 안에서 답을 다 못 낸다.
     * - 개수만큼 미리 받되, **초안이 나오지 않은 댓글 몫은 돌려준다**(실제 원가를 덮는 만큼은 남긴다). 같은 댓글에 두 번 나오거나 빈 초안은 버린다.
     */
    fun executeBatch(userId: Long, commentIds: List<Long>, tone: String): BatchAiDraftResponse {
        val ids = commentIds.distinct()
        if (ids.isEmpty()) throw BusinessException("BATCH_EMPTY", "초안을 만들 댓글을 선택해 주세요.")
        if (ids.size > MAX_BATCH_DRAFTS) {
            throw BusinessException("BATCH_TOO_LARGE", "AI 초안은 한 번에 ${MAX_BATCH_DRAFTS}개까지 만들 수 있습니다.")
        }
        rateLimiter.checkRateLimit(userId)

        val found = commentRepository.findByIds(ids)
        if (found.any { it.userId != userId }) throw ForbiddenException("해당 댓글에 대한 권한이 없습니다")
        // 요청 순서를 지킨다 — 화면이 고른 순서대로 검토한다.
        val byId = found.associateBy { it.id }
        val comments = ids.mapNotNull { byId[it] }
        if (comments.isEmpty()) {
            throw BusinessException("COMMENTS_NOT_FOUND", "댓글을 찾을 수 없습니다")
        }

        val perComment = AiFeature.BATCH_REPLY_DRAFT.creditCost
        val totalCost = perComment * comments.size

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

        val settled = creditService.withCreditsRefundingUnused(
            userId = userId,
            amount = totalCost,
            featureName = AiFeature.BATCH_REPLY_DRAFT.name,
            unusedOf = { response: BatchAiDraftResponse -> totalCost - response.totalCreditsUsed },
        ) {
            val result = try {
                chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.BATCH_REPLY_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(BatchReplyDraftResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("배치 답변 초안 생성 실패: userId={}", userId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
            }

            val drafts = result.drafts
                .filter { it.draftReply.isNotBlank() }
                .distinctBy { it.commentIndex }
                .mapNotNull { draft ->
                    val comment = comments.getOrNull(draft.commentIndex) ?: return@mapNotNull null
                    AiDraftItem(
                        commentId = comment.id!!,
                        commentContent = comment.content,
                        draftReply = draft.draftReply.trim(),
                        tone = tone,
                    )
                }
            if (drafts.isEmpty()) throw BusinessException("AI_PARSE_ERROR", "AI 가 초안을 만들지 못했습니다. 크레딧은 돌려드렸습니다.")

            BatchAiDraftResponse(
                drafts = drafts,
                totalCreditsUsed = perComment * drafts.size,
            )
        }
        // 원가 상한 때문에 덜 돌려줬을 수 있다 — 사용자에게는 실제로 가져간 크레딧을 보인다.
        return settled.value.copy(totalCreditsUsed = settled.creditsCharged)
    }

    companion object {
        const val MAX_BATCH_DRAFTS = 25
    }
}
