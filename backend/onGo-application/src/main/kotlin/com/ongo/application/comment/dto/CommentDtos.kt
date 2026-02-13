package com.ongo.application.comment.dto

import java.time.LocalDateTime

data class CommentResponse(
    val id: Long,
    val videoId: Long?,
    val platform: String?,
    val authorName: String,
    val authorAvatarUrl: String?,
    val content: String,
    val sentiment: String,
    val isReplied: Boolean,
    val replyContent: String?,
    val repliedAt: LocalDateTime?,
    val publishedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
)

data class CommentListResponse(
    val comments: List<CommentResponse>,
    val totalCount: Int,
)

data class ReplyCommentRequest(
    val content: String,
)
