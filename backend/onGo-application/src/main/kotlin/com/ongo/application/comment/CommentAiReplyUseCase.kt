package com.ongo.application.comment

import com.ongo.application.ai.GenerateReplyUseCase
import com.ongo.application.comment.dto.AiReplyCandidate
import com.ongo.application.comment.dto.AiReplyCandidatesResponse
import com.ongo.application.comment.dto.GenerateAiReplyRequest
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.comment.CommentAiReply
import com.ongo.domain.comment.CommentAiReplyRepository
import com.ongo.domain.comment.CommentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CommentAiReplyUseCase(
    private val commentRepository: CommentRepository,
    private val commentAiReplyRepository: CommentAiReplyRepository,
    private val generateReplyUseCase: GenerateReplyUseCase,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun generateReplies(userId: Long, request: GenerateAiReplyRequest): AiReplyCandidatesResponse {
        val comment = commentRepository.findById(request.commentId)
            ?: throw NotFoundException("댓글", request.commentId)

        // AI 호출 (크레딧 차감 포함) — GenerateReplyUseCase가 3가지 톤으로 답변 생성
        val result = generateReplyUseCase.execute(
            userId = userId,
            comment = comment.content,
            channelTone = request.tone ?: "FRIENDLY",
            context = null,
        )

        // 생성된 답변 후보를 DB에 저장
        val saved = result.replies.map { toneReply ->
            commentAiReplyRepository.save(
                CommentAiReply(
                    commentId = comment.id!!,
                    userId = userId,
                    generatedReply = toneReply.reply,
                    tone = toneReply.tone,
                )
            )
        }

        log.info("AI 답변 후보 생성 완료: commentId={}, count={}", comment.id, saved.size)

        return AiReplyCandidatesResponse(
            commentId = comment.id!!,
            commentContent = comment.content,
            candidates = saved.map { it.toDto() },
        )
    }

    fun markAsUsed(userId: Long, replyId: Long) {
        commentAiReplyRepository.markAsUsed(replyId)
    }

    private fun CommentAiReply.toDto() = AiReplyCandidate(
        id = id!!,
        generatedReply = generatedReply,
        tone = tone,
        isUsed = isUsed,
        createdAt = createdAt,
    )
}
