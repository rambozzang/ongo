package com.ongo.infrastructure.external.facebook

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformUploadException
import com.ongo.infrastructure.external.platform.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class FacebookClient(
    private val facebookApi: FacebookApi,
    private val facebookOAuthApi: FacebookOAuthApi,
    private val facebookConfig: FacebookConfig,
) : PlatformClient {

    private val log = LoggerFactory.getLogger(FacebookClient::class.java)

    override val platform: Platform = Platform.FACEBOOK

    override fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult {
        log.info("Facebook 영상 업로드 시작: title={}", request.title)

        val pageId = request.platformChannelId
            ?: throw PlatformUploadException("Facebook", "페이지 ID가 필요합니다")

        try {
            val response = facebookApi.uploadVideo(
                pageId = pageId,
                fileUrl = request.fileUrl,
                title = request.title.take(255),
                description = request.description.take(5000),
                accessToken = request.accessToken,
            )

            log.info("Facebook 업로드 완료: videoId={}", response.id)

            return PlatformUploadResult(
                platformVideoId = response.id,
                platformUrl = "https://www.facebook.com/${response.id}",
                status = "processing",
            )
        } catch (e: Exception) {
            log.error("Facebook 업로드 실패: {}", e.message, e)
            throw PlatformUploadException("Facebook", e.message ?: "알 수 없는 오류", e)
        }
    }

    override fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus {
        log.debug("Facebook 영상 상태 조회: videoId={}", platformVideoId)

        return try {
            val response = facebookApi.getVideo(
                videoId = platformVideoId,
                fields = "id,status,permalink_url",
                accessToken = accessToken,
            )

            PlatformVideoStatus(
                platformVideoId = platformVideoId,
                status = response.status?.videoStatus ?: "unknown",
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
        log.debug("Facebook 분석 데이터 조회: videoId={}", platformVideoId)

        return try {
            val response = facebookApi.getVideoInsights(
                videoId = platformVideoId,
                metric = "total_video_views,total_video_reactions_by_type_total,total_video_comments",
                accessToken = accessToken,
            )

            var views = 0L
            var likes = 0L
            var comments = 0L

            response.data.forEach { entry ->
                when (entry.name) {
                    "total_video_views" -> views = entry.values?.firstOrNull()?.value ?: 0
                    "total_video_reactions_by_type_total" -> likes = entry.values?.firstOrNull()?.value ?: 0
                    "total_video_comments" -> comments = entry.values?.firstOrNull()?.value ?: 0
                }
            }

            PlatformAnalytics(
                views = views,
                likes = likes,
                comments = comments,
                shares = 0,
                watchTimeSeconds = 0,
                subscriberGained = 0,
            )
        } catch (e: Exception) {
            log.warn("Facebook 분석 데이터 조회 실패: {}", e.message)
            PlatformAnalytics(0, 0, 0, 0, 0, 0)
        }
    }

    override fun getChannelInfo(accessToken: String): PlatformChannelInfo {
        log.debug("Facebook 페이지 정보 조회")

        val response = facebookApi.getPages(
            fields = "id,name,access_token,followers_count,picture,link",
            accessToken = accessToken,
        )

        val page = response.data.firstOrNull()
            ?: throw PlatformUploadException("Facebook", "관리 가능한 페이지가 없습니다")

        return PlatformChannelInfo(
            channelId = page.id,
            channelName = page.name ?: "",
            channelUrl = page.link ?: "https://www.facebook.com/${page.id}",
            subscriberCount = page.followersCount ?: 0,
            profileImageUrl = page.picture?.data?.url,
        )
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String): PlatformTokenResult {
        log.debug("Facebook OAuth 인가 코드 교환")

        val response = facebookOAuthApi.exchangeToken(
            clientId = facebookConfig.getAppId(),
            clientSecret = facebookConfig.getAppSecret(),
            code = authorizationCode,
            redirectUri = redirectUri,
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = null,
            expiresIn = response.expiresIn ?: 5184000,
        )
    }

    override fun refreshToken(refreshToken: String): PlatformTokenResult {
        log.debug("Facebook OAuth 토큰 갱신 (long-lived token exchange)")

        val response = facebookOAuthApi.refreshToken(
            grantType = "fb_exchange_token",
            clientId = facebookConfig.getAppId(),
            clientSecret = facebookConfig.getAppSecret(),
            fbExchangeToken = refreshToken,
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = null,
            expiresIn = response.expiresIn ?: 5184000,
        )
    }

    override fun deleteVideo(platformVideoId: String, accessToken: String): Boolean {
        log.info("Facebook 영상 삭제: videoId={}", platformVideoId)

        return try {
            facebookApi.deleteVideo(
                videoId = platformVideoId,
                accessToken = accessToken,
            )
            true
        } catch (e: Exception) {
            log.error("Facebook 영상 삭제 실패: {}", e.message)
            false
        }
    }

    // --- Comment API ---

    override fun getCommentCapabilities(): PlatformCommentCapabilities =
        PlatformCommentCapabilities(canListComments = true, canReply = true, canLike = true, canDelete = true, canHide = true)

    override fun listComments(
        platformVideoId: String,
        accessToken: String,
        pageToken: String?,
        maxResults: Int,
    ): PlatformCommentListResult {
        log.debug("Facebook 댓글 조회: postId={}", platformVideoId)

        return try {
            val response = facebookApi.getComments(
                postId = platformVideoId,
                fields = "id,message,from,like_count,comment_count,created_time",
                limit = maxResults.coerceAtMost(100),
                after = pageToken,
                accessToken = accessToken,
            )

            val comments = response.data?.mapNotNull { comment ->
                PlatformComment(
                    platformCommentId = comment.id ?: return@mapNotNull null,
                    authorName = comment.from?.name ?: "Unknown",
                    content = comment.message ?: "",
                    likeCount = comment.likeCount ?: 0,
                    replyCount = comment.commentCount ?: 0,
                    publishedAt = comment.createdTime?.let { parseIsoDateTime(it) },
                )
            } ?: emptyList()

            PlatformCommentListResult(
                comments = comments,
                nextPageToken = response.paging?.cursors?.after,
            )
        } catch (e: Exception) {
            log.warn("Facebook 댓글 조회 실패: {}", e.message)
            PlatformCommentListResult(emptyList())
        }
    }

    override fun replyToComment(
        platformCommentId: String,
        content: String,
        accessToken: String,
        platformVideoId: String?,
    ): PlatformCommentReplyResult {
        log.info("Facebook 댓글 답글: commentId={}", platformCommentId)

        return try {
            val response = facebookApi.replyToComment(
                commentId = platformCommentId,
                message = content,
                accessToken = accessToken,
            )
            PlatformCommentReplyResult(
                platformCommentId = response.id ?: "",
                success = response.id != null,
            )
        } catch (e: Exception) {
            log.error("Facebook 댓글 답글 실패: {}", e.message)
            PlatformCommentReplyResult("", success = false, errorMessage = e.message)
        }
    }

    override fun deleteComment(
        platformCommentId: String,
        accessToken: String,
    ): PlatformCommentDeleteResult {
        log.info("Facebook 댓글 삭제: commentId={}", platformCommentId)

        return try {
            facebookApi.deleteComment(commentId = platformCommentId, accessToken = accessToken)
            PlatformCommentDeleteResult(success = true)
        } catch (e: Exception) {
            log.error("Facebook 댓글 삭제 실패: {}", e.message)
            PlatformCommentDeleteResult(success = false, errorMessage = e.message)
        }
    }

    override fun likeComment(
        platformCommentId: String,
        accessToken: String,
    ): Boolean {
        log.info("Facebook 댓글 좋아요: commentId={}", platformCommentId)

        return try {
            val response = facebookApi.likeComment(commentId = platformCommentId, accessToken = accessToken)
            response.success ?: false
        } catch (e: Exception) {
            log.error("Facebook 댓글 좋아요 실패: {}", e.message)
            false
        }
    }

    private fun parseIsoDateTime(iso: String): java.time.LocalDateTime? =
        try {
            java.time.OffsetDateTime.parse(iso).toLocalDateTime()
        } catch (_: Exception) {
            null
        }
}
