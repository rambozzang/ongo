package com.ongo.infrastructure.external.threads

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformUploadException
import com.ongo.infrastructure.external.platform.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class ThreadsClient(
    private val threadsApi: ThreadsApi,
    private val threadsOAuthApi: ThreadsOAuthApi,
    private val threadsConfig: ThreadsConfig,
) : PlatformClient {

    private val log = LoggerFactory.getLogger(ThreadsClient::class.java)

    override val platform: Platform = Platform.THREADS

    override fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult {
        log.info("Threads 영상 업로드 시작: title={}", request.title)

        val userId = request.platformChannelId
            ?: throw PlatformUploadException("Threads", "사용자 ID가 필요합니다")

        try {
            // Step 1: Create media container
            val container = threadsApi.createMediaContainer(
                userId = userId,
                mediaType = "VIDEO",
                videoUrl = request.fileUrl,
                text = request.title.take(500),
                accessToken = request.accessToken,
            )

            // Step 2: Publish
            val published = threadsApi.publishThread(
                userId = userId,
                creationId = container.id,
                accessToken = request.accessToken,
            )

            log.info("Threads 업로드 완료: threadId={}", published.id)

            return PlatformUploadResult(
                platformVideoId = published.id,
                platformUrl = "https://www.threads.net/post/${published.id}",
                status = "published",
            )
        } catch (e: Exception) {
            log.error("Threads 업로드 실패: {}", e.message, e)
            throw PlatformUploadException("Threads", e.message ?: "알 수 없는 오류", e)
        }
    }

    override fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus {
        log.debug("Threads 상태 조회: threadId={}", platformVideoId)

        return try {
            val response = threadsApi.getThread(
                threadId = platformVideoId,
                fields = "id,media_type,permalink",
                accessToken = accessToken,
            )

            PlatformVideoStatus(
                platformVideoId = platformVideoId,
                status = if (response.id.isNotEmpty()) "published" else "not_found",
            )
        } catch (e: Exception) {
            PlatformVideoStatus(
                platformVideoId = platformVideoId,
                status = "not_found",
                errorMessage = e.message,
            )
        }
    }

    override fun getVideoAnalytics(
        platformVideoId: String,
        accessToken: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): PlatformAnalytics {
        log.debug("Threads 분석 데이터 조회: threadId={}", platformVideoId)

        return try {
            val response = threadsApi.getInsights(
                threadId = platformVideoId,
                metric = "views,likes,replies,reposts",
                accessToken = accessToken,
            )

            var views = 0L
            var likes = 0L
            var replies = 0L
            var reposts = 0L

            response.data.forEach { entry ->
                when (entry.name) {
                    "views" -> views = entry.values?.firstOrNull()?.value ?: 0
                    "likes" -> likes = entry.values?.firstOrNull()?.value ?: 0
                    "replies" -> replies = entry.values?.firstOrNull()?.value ?: 0
                    "reposts" -> reposts = entry.values?.firstOrNull()?.value ?: 0
                }
            }

            PlatformAnalytics(
                views = views,
                likes = likes,
                comments = replies,
                shares = reposts,
                watchTimeSeconds = 0,
                subscriberGained = 0,
            )
        } catch (e: Exception) {
            log.warn("Threads 분석 데이터 조회 실패: {}", e.message)
            PlatformAnalytics(0, 0, 0, 0, 0, 0)
        }
    }

    override fun getChannelInfo(accessToken: String): PlatformChannelInfo {
        log.debug("Threads 사용자 정보 조회")

        val response = threadsApi.getUser(
            fields = "id,username,name,threads_profile_picture_url",
            accessToken = accessToken,
        )

        return PlatformChannelInfo(
            channelId = response.id,
            channelName = response.name ?: response.username ?: "",
            channelUrl = response.username?.let { "https://www.threads.net/@$it" } ?: "",
            subscriberCount = 0,
            profileImageUrl = response.profilePictureUrl,
        )
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String): PlatformTokenResult {
        log.debug("Threads OAuth 인가 코드 교환")

        val shortLived = threadsOAuthApi.exchangeToken(
            clientId = threadsConfig.getAppId(),
            clientSecret = threadsConfig.getAppSecret(),
            code = authorizationCode,
            redirectUri = redirectUri,
            grantType = "authorization_code",
        )

        // Exchange for long-lived token
        val longLived = threadsOAuthApi.exchangeLongLivedToken(
            grantType = "th_exchange_token",
            clientSecret = threadsConfig.getAppSecret(),
            accessToken = shortLived.accessToken,
        )

        return PlatformTokenResult(
            accessToken = longLived.accessToken,
            refreshToken = null,
            expiresIn = longLived.expiresIn ?: 5184000,
        )
    }

    override fun refreshToken(refreshToken: String): PlatformTokenResult {
        log.debug("Threads OAuth 토큰 갱신")

        val response = threadsOAuthApi.exchangeLongLivedToken(
            grantType = "th_refresh_token",
            clientSecret = threadsConfig.getAppSecret(),
            accessToken = refreshToken,
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = null,
            expiresIn = response.expiresIn ?: 5184000,
        )
    }

    override fun deleteVideo(platformVideoId: String, accessToken: String): Boolean {
        log.info("Threads 게시물 삭제는 API에서 지원하지 않습니다: threadId={}", platformVideoId)
        return false
    }

    // --- Comment API ---

    override fun getCommentCapabilities(): PlatformCommentCapabilities =
        PlatformCommentCapabilities(canListComments = true, canReply = true, canHide = true)

    override fun listComments(
        platformVideoId: String,
        accessToken: String,
        pageToken: String?,
        maxResults: Int,
    ): PlatformCommentListResult {
        log.debug("Threads 답글 조회: mediaId={}", platformVideoId)

        return try {
            val response = threadsApi.getReplies(
                mediaId = platformVideoId,
                fields = "id,text,username,timestamp,like_count",
                limit = maxResults.coerceAtMost(100),
                after = pageToken,
                accessToken = accessToken,
            )

            val comments = response.data?.mapNotNull { reply ->
                PlatformComment(
                    platformCommentId = reply.id ?: return@mapNotNull null,
                    authorName = reply.username ?: "Unknown",
                    content = reply.text ?: "",
                    likeCount = reply.likeCount ?: 0,
                    publishedAt = reply.timestamp?.let { parseIsoDateTime(it) },
                )
            } ?: emptyList()

            PlatformCommentListResult(
                comments = comments,
                nextPageToken = response.paging?.cursors?.after,
            )
        } catch (e: Exception) {
            log.warn("Threads 답글 조회 실패: {}", e.message)
            PlatformCommentListResult(emptyList())
        }
    }

    override fun replyToComment(
        platformCommentId: String,
        content: String,
        accessToken: String,
        platformVideoId: String?,
    ): PlatformCommentReplyResult {
        log.info("Threads 답글 작성: parentId={}", platformCommentId)

        return try {
            // Get user ID from channel info
            val user = threadsApi.getUser(
                fields = "id",
                accessToken = accessToken,
            )
            val userId = user.id

            val container = threadsApi.createTextReplyContainer(
                userId = userId,
                mediaType = "TEXT",
                text = content,
                replyToId = platformCommentId,
                accessToken = accessToken,
            )
            val containerId = container.id

            val published = threadsApi.publishThread(
                userId = userId,
                creationId = containerId,
                accessToken = accessToken,
            )
            PlatformCommentReplyResult(
                platformCommentId = published.id,
                success = true,
            )
        } catch (e: Exception) {
            log.error("Threads 답글 작성 실패: {}", e.message)
            PlatformCommentReplyResult("", success = false, errorMessage = e.message)
        }
    }

    override fun deleteComment(
        platformCommentId: String,
        accessToken: String,
    ): PlatformCommentDeleteResult {
        log.info("Threads 답글 숨기기: replyId={}", platformCommentId)

        return try {
            threadsApi.hideReply(
                replyId = platformCommentId,
                hide = true,
                accessToken = accessToken,
            )
            PlatformCommentDeleteResult(success = true)
        } catch (e: Exception) {
            log.error("Threads 답글 숨기기 실패: {}", e.message)
            PlatformCommentDeleteResult(success = false, errorMessage = e.message)
        }
    }

    private fun parseIsoDateTime(iso: String): java.time.LocalDateTime? =
        try {
            java.time.OffsetDateTime.parse(iso).toLocalDateTime()
        } catch (_: Exception) {
            null
        }
}
