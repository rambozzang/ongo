package com.ongo.domain.comment

import com.ongo.common.enums.Platform
import java.time.LocalDateTime

interface PlatformCommentPort {
    fun getCommentCapabilities(platform: Platform): CommentCapabilities
    fun fetchComments(platform: Platform, platformVideoId: String, accessToken: String, pageToken: String? = null, maxResults: Int = 100): FetchedCommentList
    fun postReply(platform: Platform, platformCommentId: String, content: String, accessToken: String, platformVideoId: String? = null): PostReplyResult
    fun deleteComment(platform: Platform, platformCommentId: String, accessToken: String): DeleteCommentResult
    fun likeComment(platform: Platform, platformCommentId: String, accessToken: String): Boolean
}

data class CommentCapabilities(
    val canListComments: Boolean = false,
    val canReply: Boolean = false,
    val canLike: Boolean = false,
    val canDelete: Boolean = false,
    val canHide: Boolean = false,
)

data class FetchedComment(
    val platformCommentId: String,
    val parentCommentId: String? = null,
    val authorName: String,
    val authorAvatarUrl: String? = null,
    val authorChannelUrl: String? = null,
    val content: String,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val publishedAt: LocalDateTime? = null,
)

data class FetchedCommentList(
    val comments: List<FetchedComment>,
    val nextPageToken: String? = null,
    val totalCount: Int? = null,
)

data class PostReplyResult(
    val platformCommentId: String,
    val success: Boolean,
    val errorMessage: String? = null,
)

data class DeleteCommentResult(
    val success: Boolean,
    val errorMessage: String? = null,
)
