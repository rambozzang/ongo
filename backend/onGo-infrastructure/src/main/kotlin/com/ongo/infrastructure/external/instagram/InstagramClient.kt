package com.ongo.infrastructure.external.instagram

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformUploadException
import com.ongo.infrastructure.external.platform.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Component
class InstagramClient(
    private val instagramApi: InstagramApi,
    private val instagramOAuthApi: InstagramOAuthApi,
    private val instagramConfig: InstagramConfig,
) : PlatformClient {

    private val log = LoggerFactory.getLogger(InstagramClient::class.java)

    override val platform: Platform = Platform.INSTAGRAM

    companion object {
        private const val CONTAINER_STATUS_CHECK_INTERVAL_MS = 3000L
        private const val CONTAINER_STATUS_MAX_RETRIES = 20
    }

    override fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult {
        log.info("Instagram Reels 업로드 시작: title={}", request.title)

        val caption = buildCaption(request)

        val igUserId = request.platformChannelId
            ?: throw PlatformUploadException("Instagram", "Instagram 사용자 ID(platformChannelId)가 필요합니다")

        try {
            // Step 1: Create media container
            val containerResponse = instagramApi.createMediaContainer(
                igUserId = igUserId,
                mediaType = "REELS",
                videoUrl = request.fileUrl,
                caption = caption,
                shareToFeed = true,
                accessToken = request.accessToken,
            )

            if (containerResponse.error != null) {
                throw PlatformUploadException(
                    "Instagram",
                    "컨테이너 생성 실패: ${containerResponse.error.message}",
                )
            }

            val containerId = containerResponse.id
                ?: throw PlatformUploadException("Instagram", "컨테이너 ID를 받지 못했습니다")

            log.debug("Instagram 컨테이너 생성 완료: containerId={}", containerId)

            // Step 2: Wait for container processing
            waitForContainerReady(containerId, request.accessToken)

            // Step 3: Publish the container
            val publishResponse = instagramApi.publishMedia(
                igUserId = igUserId,
                creationId = containerId,
                accessToken = request.accessToken,
            )

            if (publishResponse.error != null) {
                throw PlatformUploadException(
                    "Instagram",
                    "게시 실패: ${publishResponse.error.message}",
                )
            }

            val mediaId = publishResponse.id
                ?: throw PlatformUploadException("Instagram", "미디어 ID를 받지 못했습니다")

            log.info("Instagram Reels 게시 완료: mediaId={}", mediaId)

            // Get permalink
            val mediaDetail = instagramApi.getMedia(
                mediaId = mediaId,
                fields = "id,permalink",
                accessToken = request.accessToken,
            )

            return PlatformUploadResult(
                platformVideoId = mediaId,
                platformUrl = mediaDetail.permalink ?: "",
                status = "PUBLISHED",
            )
        } catch (e: PlatformUploadException) {
            throw e
        } catch (e: Exception) {
            log.error("Instagram 업로드 실패: {}", e.message, e)
            throw PlatformUploadException("Instagram", e.message ?: "알 수 없는 오류", e)
        }
    }

    override fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus {
        log.debug("Instagram 미디어 상태 조회: mediaId={}", platformVideoId)

        try {
            val response = instagramApi.getContainerStatus(
                containerId = platformVideoId,
                fields = "id,status_code",
                accessToken = accessToken,
            )

            return PlatformVideoStatus(
                platformVideoId = platformVideoId,
                status = response.statusCode ?: "unknown",
                errorMessage = response.error?.message,
            )
        } catch (e: Exception) {
            log.warn("Instagram 상태 조회 실패: {}", e.message)
            return PlatformVideoStatus(
                platformVideoId = platformVideoId,
                status = "unknown",
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
        log.debug("Instagram 분석 데이터 조회: mediaId={}", platformVideoId)

        try {
            val insightsResponse = instagramApi.getMediaInsights(
                mediaId = platformVideoId,
                metric = "plays,likes,comments,shares,saved",
                accessToken = accessToken,
            )

            val metrics = insightsResponse.data?.associate {
                (it.name ?: "") to (it.values?.firstOrNull()?.value ?: 0L)
            } ?: emptyMap()

            return PlatformAnalytics(
                views = metrics["plays"] ?: 0,
                likes = metrics["likes"] ?: 0,
                comments = metrics["comments"] ?: 0,
                shares = metrics["shares"] ?: 0,
                watchTimeSeconds = 0, // Instagram insights don't directly expose watch time
                subscriberGained = 0,
            )
        } catch (e: Exception) {
            log.warn("Instagram 분석 데이터 조회 실패: {}", e.message)
            return PlatformAnalytics(0, 0, 0, 0, 0, 0)
        }
    }

    override fun getChannelInfo(accessToken: String): PlatformChannelInfo {
        log.debug("Instagram 프로필 정보 조회")

        val response = instagramApi.getUserProfile(
            fields = "id,username,name,profile_picture_url,followers_count,media_count",
            accessToken = accessToken,
        )

        if (response.error != null) {
            throw PlatformUploadException("Instagram", "프로필 정보 조회 실패: ${response.error.message}")
        }

        return PlatformChannelInfo(
            channelId = response.id ?: "",
            channelName = response.name ?: response.username ?: "",
            channelUrl = response.username?.let { "https://www.instagram.com/$it" } ?: "",
            subscriberCount = response.followersCount ?: 0,
            profileImageUrl = response.profilePictureUrl,
        )
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String): PlatformTokenResult {
        log.debug("Instagram OAuth 인가 코드 교환")

        val response = instagramOAuthApi.exchangeCodeForToken(
            mapOf(
                "client_id" to instagramConfig.getAppId(),
                "client_secret" to instagramConfig.getAppSecret(),
                "code" to authorizationCode,
                "redirect_uri" to redirectUri,
                "grant_type" to "authorization_code",
            ),
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = null, // Instagram uses same token as refresh mechanism
            expiresIn = response.expiresIn ?: 3600, // Short-lived token, 1 hour default
        )
    }

    override fun refreshToken(refreshToken: String): PlatformTokenResult {
        log.debug("Instagram 토큰 갱신 (Long-Lived Token)")

        // Instagram uses long-lived tokens refreshed via GET request
        val response = instagramOAuthApi.refreshLongLivedToken(
            grantType = "ig_refresh_token",
            accessToken = refreshToken,
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = null, // Instagram uses same token as refresh mechanism
            expiresIn = response.expiresIn ?: 5184000, // 60 days default
        )
    }

    override fun deleteVideo(platformVideoId: String, accessToken: String): Boolean {
        log.info("Instagram 미디어 삭제: mediaId={}", platformVideoId)
        // Instagram Graph API does not support Reels deletion via API.
        log.warn("Instagram API는 Reels 삭제를 지원하지 않습니다")
        return false
    }

    // --- Comment API ---

    override fun getCommentCapabilities(): PlatformCommentCapabilities =
        PlatformCommentCapabilities(canListComments = true, canReply = true, canDelete = true, canHide = true)

    override fun listComments(
        platformVideoId: String,
        accessToken: String,
        pageToken: String?,
        maxResults: Int,
    ): PlatformCommentListResult {
        log.debug("Instagram 댓글 조회: mediaId={}", platformVideoId)

        return try {
            val response = instagramApi.getComments(
                mediaId = platformVideoId,
                fields = "id,text,username,like_count,timestamp",
                limit = maxResults.coerceAtMost(100),
                after = pageToken,
                accessToken = accessToken,
            )

            val comments = response.data?.mapNotNull { comment ->
                PlatformComment(
                    platformCommentId = comment.id ?: return@mapNotNull null,
                    authorName = comment.username ?: "Unknown",
                    content = comment.text ?: "",
                    likeCount = comment.likeCount ?: 0,
                    publishedAt = comment.timestamp?.let { parseIsoDateTime(it) },
                )
            } ?: emptyList()

            PlatformCommentListResult(
                comments = comments,
                nextPageToken = response.paging?.cursors?.after,
            )
        } catch (e: Exception) {
            log.warn("Instagram 댓글 조회 실패: {}", e.message)
            PlatformCommentListResult(emptyList())
        }
    }

    override fun replyToComment(
        platformCommentId: String,
        content: String,
        accessToken: String,
        platformVideoId: String?,
    ): PlatformCommentReplyResult {
        log.info("Instagram 댓글 답글: commentId={}", platformCommentId)

        return try {
            val response = instagramApi.replyToComment(
                commentId = platformCommentId,
                message = content,
                accessToken = accessToken,
            )
            PlatformCommentReplyResult(
                platformCommentId = response.id ?: "",
                success = response.id != null,
            )
        } catch (e: Exception) {
            log.error("Instagram 댓글 답글 실패: {}", e.message)
            PlatformCommentReplyResult("", success = false, errorMessage = e.message)
        }
    }

    override fun deleteComment(
        platformCommentId: String,
        accessToken: String,
    ): PlatformCommentDeleteResult {
        log.info("Instagram 댓글 삭제: commentId={}", platformCommentId)

        return try {
            instagramApi.deleteComment(
                commentId = platformCommentId,
                accessToken = accessToken,
            )
            PlatformCommentDeleteResult(success = true)
        } catch (e: Exception) {
            log.error("Instagram 댓글 삭제 실패: {}", e.message)
            PlatformCommentDeleteResult(success = false, errorMessage = e.message)
        }
    }

    private fun parseIsoDateTime(iso: String): java.time.LocalDateTime? =
        try {
            java.time.OffsetDateTime.parse(iso).toLocalDateTime()
        } catch (_: Exception) {
            null
        }

    private fun buildCaption(request: PlatformUploadRequest): String {
        val hashtags = request.tags.joinToString(" ") { "#$it" }
        val description = request.description.take(2200 - hashtags.length - 2)
        return "$description\n\n$hashtags".take(2200)
    }

    private fun waitForContainerReady(containerId: String, accessToken: String) {
        var retries = 0
        while (retries < CONTAINER_STATUS_MAX_RETRIES) {
            val status = instagramApi.getContainerStatus(
                containerId = containerId,
                fields = "id,status_code",
                accessToken = accessToken,
            )

            when (status.statusCode) {
                "FINISHED" -> {
                    log.debug("Instagram 컨테이너 처리 완료: containerId={}", containerId)
                    return
                }
                "ERROR" -> {
                    throw PlatformUploadException("Instagram", "컨테이너 처리 중 오류 발생")
                }
                "EXPIRED" -> {
                    throw PlatformUploadException("Instagram", "컨테이너가 만료되었습니다")
                }
                "IN_PROGRESS" -> {
                    log.debug("Instagram 컨테이너 처리 중... ({}회 확인)", retries + 1)
                    TimeUnit.MILLISECONDS.sleep(CONTAINER_STATUS_CHECK_INTERVAL_MS)
                    retries++
                }
                else -> {
                    TimeUnit.MILLISECONDS.sleep(CONTAINER_STATUS_CHECK_INTERVAL_MS)
                    retries++
                }
            }
        }

        throw PlatformUploadException("Instagram", "컨테이너 처리 시간 초과")
    }
}
