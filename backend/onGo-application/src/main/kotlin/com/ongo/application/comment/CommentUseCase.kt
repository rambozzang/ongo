package com.ongo.application.comment

import com.ongo.application.comment.dto.CommentListResponse
import com.ongo.application.comment.dto.CommentResponse
import com.ongo.application.comment.dto.ReplyCommentRequest
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.comment.Comment
import com.ongo.domain.comment.CommentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CommentUseCase(
    private val commentRepository: CommentRepository,
) {

    fun listComments(
        userId: Long,
        videoId: Long?,
        platform: String?,
        sentiment: String?,
        page: Int,
        size: Int,
    ): CommentListResponse {
        val comments = when {
            videoId != null -> commentRepository.findByUserIdAndVideoId(userId, videoId, page, size)
            platform != null -> commentRepository.findByUserIdAndPlatform(userId, platform, page, size)
            sentiment != null -> commentRepository.findByUserIdAndSentiment(userId, sentiment, page, size)
            else -> commentRepository.findByUserId(userId, page, size)
        }
        val totalCount = commentRepository.countByUserId(userId)

        return CommentListResponse(
            comments = comments.map { it.toResponse() },
            totalCount = totalCount,
        )
    }

    @Transactional
    fun replyToComment(userId: Long, commentId: Long, request: ReplyCommentRequest): CommentResponse {
        val comment = commentRepository.findById(commentId) ?: throw NotFoundException("댓글", commentId)
        if (comment.userId != userId) throw ForbiddenException("해당 댓글에 대한 권한이 없습니다")

        val updated = comment.copy(
            isReplied = true,
            replyContent = request.content,
            repliedAt = LocalDateTime.now(),
        )
        return commentRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteComment(userId: Long, commentId: Long) {
        val comment = commentRepository.findById(commentId) ?: throw NotFoundException("댓글", commentId)
        if (comment.userId != userId) throw ForbiddenException("해당 댓글에 대한 권한이 없습니다")
        commentRepository.delete(commentId)
    }

    private fun Comment.toResponse(): CommentResponse = CommentResponse(
        id = id!!,
        videoId = videoId,
        platform = platform,
        authorName = authorName,
        authorAvatarUrl = authorAvatarUrl,
        content = content,
        sentiment = sentiment,
        isReplied = isReplied,
        replyContent = replyContent,
        repliedAt = repliedAt,
        publishedAt = publishedAt,
        createdAt = createdAt,
    )
}
