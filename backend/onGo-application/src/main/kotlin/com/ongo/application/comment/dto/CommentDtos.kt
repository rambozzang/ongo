package com.ongo.application.comment.dto

import java.time.LocalDateTime

data class CommentResponse(
    val id: Long,
    val videoId: Long?,
    val platform: String?,
    val platformCommentId: String?,
    val authorName: String,
    val authorAvatarUrl: String?,
    val authorChannelUrl: String?,
    val content: String,
    val sentiment: String,
    val likeCount: Int,
    val replyCount: Int,
    val isReplied: Boolean,
    val isHidden: Boolean,
    val isPinned: Boolean,
    val replyContent: String?,
    val repliedAt: LocalDateTime?,
    val publishedAt: LocalDateTime?,
    val syncedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
)

data class CommentListResponse(
    val comments: List<CommentResponse>,
    val totalCount: Int,
    val stats: CommentStats,
    val capabilities: Map<String, CommentCapabilitiesDto>,
)

data class CommentStats(
    val total: Int,
    val positive: Int,
    val neutral: Int,
    val negative: Int,
)

data class CommentCapabilitiesDto(
    val canListComments: Boolean,
    val canReply: Boolean,
    val canLike: Boolean,
    val canDelete: Boolean,
    val canHide: Boolean,
)

data class CommentSyncResult(
    val totalSynced: Int,
    val totalNew: Int,
    val errors: List<String>,
)

data class ReplyCommentRequest(
    val content: String,
)
