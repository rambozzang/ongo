package com.ongo.infrastructure.external.facebook

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformApiException
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
                description = buildDescription(request),
                accessToken = request.accessToken.value,
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
            throw PlatformApiException("Facebook", "영상 상태 조회 실패", e)
        }
    }

    private fun buildDescription(request: PlatformUploadRequest): String {
        val hashtags = request.tags
            .map { it.removePrefix("#").trim() }
            .filter(String::isNotBlank)
            .joinToString(" ") { "#$it" }
        return listOf(request.description.trim(), hashtags)
            .filter(String::isNotBlank)
            .joinToString("\n\n")
            .take(5000)
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

            /*
             * **`0L` 로 시작하면 "응답에 그 지표가 없었다" 가 실측 0 과 같아진다.**
             *
             * 세 지표 모두 위 `metric` 목록으로 요청했으므로 응답에 빠진 것은 미지원이
             * 아니라 응답 이상이다. 엔트리가 `0` 을 주면 그 0 은 그대로 관측이다.
             */
            var views: Long? = null
            var likes: Long? = null
            var comments: Long? = null

            response.data.forEach { entry ->
                val value = entry.values?.firstOrNull()?.value
                when (entry.name) {
                    "total_video_views" -> views = value
                    "total_video_reactions_by_type_total" -> likes = value
                    "total_video_comments" -> comments = value
                }
            }

            PlatformAnalytics(
                views = views.requireMetric("Facebook", "total_video_views"),
                // 반응 합계를 좋아요로 쓴다 — availability 주석 참조. 값의 존재 여부만 검증한다.
                likes = likes.requireMetric("Facebook", "total_video_reactions_by_type_total"),
                comments = comments.requireMetric("Facebook", "total_video_comments"),
                // 공유는 이 엔드포인트가 주지 않는다 — availability 가 미수집으로 선언한다.
                shares = 0,
                watchTimeSeconds = 0,
                subscriberGained = 0,
            )
        } catch (e: PlatformApiException) {
            // 어떤 지표가 빠졌는지 담은 메시지를 그대로 올려 보낸다. 일반 메시지로 덮으면
            // 스케줄러 경고 로그에서 원인 지표를 알 수 없다.
            throw e
        } catch (e: Exception) {
            log.warn("Facebook 분석 데이터 조회 실패: {}", e.message)
            throw PlatformApiException("Facebook", "분석 데이터 조회 실패", e)
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
            subscriberCount = page.followersCount,
            profileImageUrl = page.picture?.data?.url,
        )
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String, codeVerifier: String?): PlatformTokenResult {
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
        publishedAfter: java.time.LocalDateTime?,
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
