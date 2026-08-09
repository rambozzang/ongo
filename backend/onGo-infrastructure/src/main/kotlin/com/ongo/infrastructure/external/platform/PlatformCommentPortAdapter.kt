package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformApiException
import com.ongo.domain.comment.*
import com.ongo.domain.channel.PlainToken
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
        accessToken: PlainToken,
        pageToken: String?,
        maxResults: Int,
        publishedAfter: java.time.LocalDateTime?,
    ): FetchedCommentList {
        val result = platformClientFactory.getClient(platform)
            .listComments(platformVideoId, accessToken.value, pageToken, maxResults, publishedAfter)
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
        accessToken: PlainToken,
        platformVideoId: String?,
    ): PostReplyResult {
        val result = platformClientFactory.getClient(platform)
            .replyToComment(platformCommentId, content, accessToken.value, platformVideoId)
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
        accessToken: PlainToken,
    ): DeleteCommentResult {
        val result = platformClientFactory.getClient(platform)
            .deleteComment(platformCommentId, accessToken.value)
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
        accessToken: PlainToken,
    ): Boolean {
        return platformClientFactory.getClient(platform)
            .likeComment(platformCommentId, accessToken.value)
    }

    @Suppress("unused")
    private fun fetchCommentsFallback(
        platform: Platform,
        platformVideoId: String,
        accessToken: PlainToken,
        pageToken: String?,
        maxResults: Int,
        publishedAfter: java.time.LocalDateTime?,
        e: Throwable,
    ): FetchedCommentList {
        log.warn("플랫폼 {} 댓글 조회 실패 (Circuit Breaker): {}", platform, e.message)
        throw PlatformApiException(platform.name, "댓글 조회 Circuit Breaker 발생: ${e.message}", e)
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
