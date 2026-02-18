package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Platform
import com.ongo.domain.comment.*
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PlatformCommentPortAdapter(
    private val platformClientFactory: PlatformClientFactory,
) : PlatformCommentPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getCommentCapabilities(platform: Platform): CommentCapabilities {
        val caps = platformClientFactory.getClient(platform).getCommentCapabilities()
        return CommentCapabilities(
            canListComments = caps.canListComments,
            canReply = caps.canReply,
            canLike = caps.canLike,
            canDelete = caps.canDelete,
            canHide = caps.canHide,
        )
    }

    @Retry(name = "platformApi")
    @CircuitBreaker(name = "platformApi", fallbackMethod = "fetchCommentsFallback")
    override fun fetchComments(
        platform: Platform,
        platformVideoId: String,
        accessToken: String,
        pageToken: String?,
        maxResults: Int,
    ): FetchedCommentList {
        val result = platformClientFactory.getClient(platform)
            .listComments(platformVideoId, accessToken, pageToken, maxResults)
        return FetchedCommentList(
            comments = result.comments.map { it.toDomain() },
            nextPageToken = result.nextPageToken,
            totalCount = result.totalCount,
        )
    }

    @Retry(name = "platformApi")
    @CircuitBreaker(name = "platformApi")
    override fun postReply(
        platform: Platform,
        platformCommentId: String,
        content: String,
        accessToken: String,
        platformVideoId: String?,
    ): PostReplyResult {
        val result = platformClientFactory.getClient(platform)
            .replyToComment(platformCommentId, content, accessToken, platformVideoId)
        return PostReplyResult(
            platformCommentId = result.platformCommentId,
            success = result.success,
            errorMessage = result.errorMessage,
        )
    }

    @Retry(name = "platformApi")
    @CircuitBreaker(name = "platformApi")
    override fun deleteComment(
        platform: Platform,
        platformCommentId: String,
        accessToken: String,
    ): DeleteCommentResult {
        val result = platformClientFactory.getClient(platform)
            .deleteComment(platformCommentId, accessToken)
        return DeleteCommentResult(
            success = result.success,
            errorMessage = result.errorMessage,
        )
    }

    @Retry(name = "platformApi")
    @CircuitBreaker(name = "platformApi")
    override fun likeComment(
        platform: Platform,
        platformCommentId: String,
        accessToken: String,
    ): Boolean {
        return platformClientFactory.getClient(platform)
            .likeComment(platformCommentId, accessToken)
    }

    @Suppress("unused")
    private fun fetchCommentsFallback(
        platform: Platform,
        platformVideoId: String,
        accessToken: String,
        pageToken: String?,
        maxResults: Int,
        e: Throwable,
    ): FetchedCommentList {
        log.warn("플랫폼 {} 댓글 조회 실패 (Circuit Breaker): {}", platform, e.message)
        return FetchedCommentList(emptyList())
    }

    private fun PlatformComment.toDomain(): FetchedComment = FetchedComment(
        platformCommentId = platformCommentId,
        parentCommentId = parentCommentId,
        authorName = authorName,
        authorAvatarUrl = authorAvatarUrl,
        authorChannelUrl = authorChannelUrl,
        content = content,
        likeCount = likeCount,
        replyCount = replyCount,
        publishedAt = publishedAt,
    )
}
