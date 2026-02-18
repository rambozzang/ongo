package com.ongo.infrastructure.external.wordpress

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformUploadException
import com.ongo.infrastructure.external.platform.*
import com.ongo.infrastructure.external.wordpress.dto.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class WordPressClient(
    private val wordPressApi: WordPressApi,
    private val wordPressOAuthApi: WordPressOAuthApi,
    private val wordPressConfig: WordPressConfig,
) : PlatformClient {

    private val log = LoggerFactory.getLogger(WordPressClient::class.java)

    override val platform: Platform = Platform.WORDPRESS

    override fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult {
        log.info("WordPress 영상 업로드 시작: title={}", request.title)

        val siteId = request.platformChannelId
            ?: throw PlatformUploadException("WordPress", "사이트 ID가 필요합니다")

        try {
            // Step 1: Upload media
            val mediaResponse = wordPressApi.uploadMedia(
                siteId = siteId,
                mediaUrl = request.fileUrl,
                authorization = "Bearer ${request.accessToken}",
            )

            // Step 2: Create post with embedded video
            val videoEmbed = """<video src="${mediaResponse.url}" controls width="100%"></video>"""
            val content = "$videoEmbed\n\n${request.description}"

            val postRequest = WordPressPostRequest(
                title = request.title.take(200),
                content = content,
                status = mapVisibility(request.visibility),
                tags = request.tags,
                format = "video",
            )

            val postResponse = wordPressApi.createPost(
                siteId = siteId,
                authorization = "Bearer ${request.accessToken}",
                request = postRequest,
            )

            log.info("WordPress 업로드 완료: postId={}", postResponse.id)

            return PlatformUploadResult(
                platformVideoId = postResponse.id.toString(),
                platformUrl = postResponse.url ?: postResponse.shortUrl ?: "",
                status = postResponse.status ?: "published",
            )
        } catch (e: Exception) {
            log.error("WordPress 업로드 실패: {}", e.message, e)
            throw PlatformUploadException("WordPress", e.message ?: "알 수 없는 오류", e)
        }
    }

    override fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus {
        log.debug("WordPress 게시물 상태 조회: postId={}", platformVideoId)

        return PlatformVideoStatus(
            platformVideoId = platformVideoId,
            status = "published",
        )
    }

    override fun getVideoAnalytics(
        platformVideoId: String,
        accessToken: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): PlatformAnalytics {
        log.debug("WordPress 분석 데이터 조회: postId={}", platformVideoId)

        return try {
            val response = wordPressApi.getPostStats(
                siteId = "me",
                postId = platformVideoId,
                authorization = "Bearer $accessToken",
            )

            PlatformAnalytics(
                views = response.views ?: 0,
                likes = response.likes ?: 0,
                comments = response.comments ?: 0,
                shares = 0,
                watchTimeSeconds = 0,
                subscriberGained = 0,
            )
        } catch (e: Exception) {
            log.warn("WordPress 분석 데이터 조회 실패: {}", e.message)
            PlatformAnalytics(0, 0, 0, 0, 0, 0)
        }
    }

    override fun getChannelInfo(accessToken: String): PlatformChannelInfo {
        log.debug("WordPress 사용자 정보 조회")

        val response = wordPressApi.getMe(
            authorization = "Bearer $accessToken",
        )

        val blog = response.primaryBlog

        return PlatformChannelInfo(
            channelId = (blog?.id ?: response.id).toString(),
            channelName = blog?.name ?: response.displayName ?: "",
            channelUrl = blog?.url ?: response.primaryBlogUrl ?: "",
            subscriberCount = blog?.subscribers_count ?: 0,
            profileImageUrl = response.avatarUrl,
        )
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String): PlatformTokenResult {
        log.debug("WordPress OAuth 인가 코드 교환")

        val response = wordPressOAuthApi.exchangeToken(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to authorizationCode,
                "redirect_uri" to redirectUri,
                "client_id" to wordPressConfig.getClientId(),
                "client_secret" to wordPressConfig.getClientSecret(),
            ),
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = null,
            expiresIn = 0, // WordPress.com tokens don't expire
        )
    }

    override fun refreshToken(refreshToken: String): PlatformTokenResult {
        log.debug("WordPress OAuth 토큰 갱신 (WordPress.com 토큰은 만료되지 않음)")

        return PlatformTokenResult(
            accessToken = refreshToken,
            refreshToken = null,
            expiresIn = 0,
        )
    }

    override fun deleteVideo(platformVideoId: String, accessToken: String): Boolean {
        log.info("WordPress 게시물 삭제: postId={}", platformVideoId)

        return try {
            wordPressApi.deletePost(
                siteId = "me",
                postId = platformVideoId,
                authorization = "Bearer $accessToken",
            )
            true
        } catch (e: Exception) {
            log.error("WordPress 게시물 삭제 실패: {}", e.message)
            false
        }
    }

    // --- Comment API ---

    override fun getCommentCapabilities(): PlatformCommentCapabilities =
        PlatformCommentCapabilities(canListComments = true, canReply = true, canLike = true, canDelete = true)

    override fun listComments(
        platformVideoId: String,
        accessToken: String,
        pageToken: String?,
        maxResults: Int,
    ): PlatformCommentListResult {
        log.debug("WordPress 댓글 조회: postId={}", platformVideoId)

        return try {
            val page = pageToken?.toIntOrNull() ?: 1
            // platformVideoId format: "siteId:postId"
            val parts = platformVideoId.split(":")
            val siteId = parts.getOrElse(0) { platformVideoId }
            val postId = parts.getOrElse(1) { platformVideoId }

            val response = wordPressApi.getComments(
                siteId = siteId,
                postId = postId,
                number = maxResults.coerceAtMost(100),
                page = page,
                authorization = "Bearer $accessToken",
            )

            val comments = response.comments?.mapNotNull { comment ->
                PlatformComment(
                    platformCommentId = comment.id ?: return@mapNotNull null,
                    authorName = comment.author?.name ?: "Unknown",
                    authorAvatarUrl = comment.author?.avatarUrl,
                    authorChannelUrl = comment.author?.url,
                    content = comment.content ?: "",
                    likeCount = comment.likeCount ?: 0,
                    publishedAt = comment.date?.let { parseIsoDateTime(it) },
                )
            } ?: emptyList()

            val hasMore = (response.found ?: 0) > page * maxResults
            PlatformCommentListResult(
                comments = comments,
                nextPageToken = if (hasMore) (page + 1).toString() else null,
                totalCount = response.found,
            )
        } catch (e: Exception) {
            log.warn("WordPress 댓글 조회 실패: {}", e.message)
            PlatformCommentListResult(emptyList())
        }
    }

    override fun replyToComment(
        platformCommentId: String,
        content: String,
        accessToken: String,
        platformVideoId: String?,
    ): PlatformCommentReplyResult {
        log.info("WordPress 댓글 답글: commentId={}", platformCommentId)

        return try {
            val parts = (platformVideoId ?: "").split(":")
            val siteId = parts.getOrElse(0) { "" }
            val postId = parts.getOrElse(1) { "" }

            val response = wordPressApi.createComment(
                siteId = siteId,
                postId = postId,
                authorization = "Bearer $accessToken",
                body = WordPressCommentRequest(content = content, parentId = platformCommentId),
            )
            PlatformCommentReplyResult(
                platformCommentId = response.id ?: "",
                success = response.id != null,
            )
        } catch (e: Exception) {
            log.error("WordPress 댓글 답글 실패: {}", e.message)
            PlatformCommentReplyResult("", success = false, errorMessage = e.message)
        }
    }

    override fun deleteComment(
        platformCommentId: String,
        accessToken: String,
    ): PlatformCommentDeleteResult {
        log.info("WordPress 댓글 삭제: commentId={}", platformCommentId)

        return try {
            // Need siteId - extract from comment context or use a default
            wordPressApi.deleteComment(
                siteId = "default",
                commentId = platformCommentId,
                authorization = "Bearer $accessToken",
            )
            PlatformCommentDeleteResult(success = true)
        } catch (e: Exception) {
            log.error("WordPress 댓글 삭제 실패: {}", e.message)
            PlatformCommentDeleteResult(success = false, errorMessage = e.message)
        }
    }

    override fun likeComment(
        platformCommentId: String,
        accessToken: String,
    ): Boolean {
        log.info("WordPress 댓글 좋아요: commentId={}", platformCommentId)

        return try {
            val response = wordPressApi.likeComment(
                siteId = "default",
                commentId = platformCommentId,
                authorization = "Bearer $accessToken",
            )
            response.success ?: false
        } catch (e: Exception) {
            log.error("WordPress 댓글 좋아요 실패: {}", e.message)
            false
        }
    }

    private fun parseIsoDateTime(iso: String): java.time.LocalDateTime? =
        try {
            java.time.OffsetDateTime.parse(iso).toLocalDateTime()
        } catch (_: Exception) {
            null
        }

    private fun mapVisibility(visibility: String): String =
        when (visibility.uppercase()) {
            "PUBLIC" -> "publish"
            "PRIVATE" -> "private"
            "UNLISTED" -> "draft"
            else -> "draft"
        }
}
