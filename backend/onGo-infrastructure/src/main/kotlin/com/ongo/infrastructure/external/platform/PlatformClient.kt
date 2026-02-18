package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Platform
import java.time.LocalDate
import java.time.LocalDateTime

interface PlatformClient {
    val platform: Platform

    fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult
    fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus
    fun getVideoAnalytics(platformVideoId: String, accessToken: String, startDate: LocalDate, endDate: LocalDate): PlatformAnalytics
    fun getChannelInfo(accessToken: String): PlatformChannelInfo
    fun refreshToken(refreshToken: String): PlatformTokenResult
    fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String): PlatformTokenResult
    fun deleteVideo(platformVideoId: String, accessToken: String): Boolean

    // --- Comment API (default implementations for unsupported platforms) ---

    fun getCommentCapabilities(): PlatformCommentCapabilities = PlatformCommentCapabilities()

    fun listComments(
        platformVideoId: String,
        accessToken: String,
        pageToken: String? = null,
        maxResults: Int = 100,
    ): PlatformCommentListResult = PlatformCommentListResult(emptyList())

    fun replyToComment(
        platformCommentId: String,
        content: String,
        accessToken: String,
        platformVideoId: String? = null,
    ): PlatformCommentReplyResult = throw UnsupportedOperationException("${platform.name} does not support comment replies")

    fun deleteComment(
        platformCommentId: String,
        accessToken: String,
    ): PlatformCommentDeleteResult = throw UnsupportedOperationException("${platform.name} does not support comment deletion")

    fun likeComment(
        platformCommentId: String,
        accessToken: String,
    ): Boolean = throw UnsupportedOperationException("${platform.name} does not support comment likes")
}

data class PlatformUploadRequest(
    val fileUrl: String,
    val title: String,
    val description: String,
    val tags: List<String>,
    val visibility: String,
    val thumbnailUrl: String?,
    val accessToken: String,
    val platformChannelId: String? = null,
    val fileSize: Long = 0,
    val scheduledAt: LocalDateTime? = null,
)

data class PlatformUploadResult(
    val platformVideoId: String,
    val platformUrl: String,
    val status: String,
)

data class PlatformVideoStatus(
    val platformVideoId: String,
    val status: String,
    val errorMessage: String? = null,
)

data class PlatformAnalytics(
    val views: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val watchTimeSeconds: Long,
    val subscriberGained: Int,
)

data class PlatformChannelInfo(
    val channelId: String,
    val channelName: String,
    val channelUrl: String,
    val subscriberCount: Long,
    val profileImageUrl: String?,
)

data class PlatformTokenResult(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Long,
)

// --- Comment data classes ---

data class PlatformCommentCapabilities(
    val canListComments: Boolean = false,
    val canReply: Boolean = false,
    val canLike: Boolean = false,
    val canDelete: Boolean = false,
    val canHide: Boolean = false,
)

data class PlatformComment(
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

data class PlatformCommentListResult(
    val comments: List<PlatformComment>,
    val nextPageToken: String? = null,
    val totalCount: Int? = null,
)

data class PlatformCommentReplyResult(
    val platformCommentId: String,
    val success: Boolean = true,
    val errorMessage: String? = null,
)

data class PlatformCommentDeleteResult(
    val success: Boolean = true,
    val errorMessage: String? = null,
)
