package com.ongo.infrastructure.external.vimeo

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformUploadException
import com.ongo.infrastructure.external.platform.*
import com.ongo.infrastructure.external.vimeo.dto.VimeoCommentRequest
import com.ongo.infrastructure.external.vimeo.dto.VimeoUploadRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class VimeoClient(
    private val vimeoApi: VimeoApi,
    private val vimeoOAuthApi: VimeoOAuthApi,
    private val vimeoConfig: VimeoConfig,
) : PlatformClient {

    private val log = LoggerFactory.getLogger(VimeoClient::class.java)

    override val platform: Platform = Platform.VIMEO

    override fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult {
        log.info("Vimeo 영상 업로드 시작: title={}", request.title)

        val privacyView = mapVisibility(request.visibility)

        val uploadRequest = VimeoUploadRequest(
            upload = VimeoUploadRequest.Upload(
                approach = "pull",
                link = request.fileUrl,
            ),
            name = request.title.take(128),
            description = request.description.take(5000),
            privacy = VimeoUploadRequest.Privacy(
                view = privacyView,
            ),
        )

        try {
            val response = vimeoApi.createVideo(
                authorization = "Bearer ${request.accessToken}",
                request = uploadRequest,
            )

            val videoId = response.uri.substringAfterLast("/")
            log.info("Vimeo 업로드 완료: videoId={}", videoId)

            return PlatformUploadResult(
                platformVideoId = videoId,
                platformUrl = response.link ?: "https://vimeo.com/$videoId",
                status = response.status ?: "uploading",
            )
        } catch (e: Exception) {
            log.error("Vimeo 업로드 실패: {}", e.message, e)
            throw PlatformUploadException("Vimeo", e.message ?: "알 수 없는 오류", e)
        }
    }

    override fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus {
        log.debug("Vimeo 영상 상태 조회: videoId={}", platformVideoId)

        return try {
            val response = vimeoApi.getVideo(
                videoId = platformVideoId,
                authorization = "Bearer $accessToken",
            )

            PlatformVideoStatus(
                platformVideoId = platformVideoId,
                status = response.status ?: "unknown",
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
        log.debug("Vimeo 분석 데이터 조회: videoId={}", platformVideoId)

        return try {
            val response = vimeoApi.getVideo(
                videoId = platformVideoId,
                authorization = "Bearer $accessToken",
            )

            PlatformAnalytics(
                views = response.stats?.plays ?: 0,
                likes = response.metadata?.connections?.likes?.total ?: 0,
                comments = response.metadata?.connections?.comments?.total ?: 0,
                shares = 0,
                watchTimeSeconds = 0,
                subscriberGained = 0,
            )
        } catch (e: Exception) {
            log.warn("Vimeo 분석 데이터 조회 실패: {}", e.message)
            PlatformAnalytics(0, 0, 0, 0, 0, 0)
        }
    }

    override fun getChannelInfo(accessToken: String): PlatformChannelInfo {
        log.debug("Vimeo 사용자 정보 조회")

        val response = vimeoApi.getUser(
            authorization = "Bearer $accessToken",
        )

        val userId = response.uri.substringAfterLast("/")
        val profileImage = response.pictures?.sizes
            ?.maxByOrNull { it.width ?: 0 }?.link

        return PlatformChannelInfo(
            channelId = userId,
            channelName = response.name ?: "",
            channelUrl = response.link ?: "https://vimeo.com/$userId",
            subscriberCount = response.metadata?.connections?.followers?.total ?: 0,
            profileImageUrl = profileImage,
        )
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String): PlatformTokenResult {
        log.debug("Vimeo OAuth 인가 코드 교환")

        val response = vimeoOAuthApi.exchangeToken(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to authorizationCode,
                "redirect_uri" to redirectUri,
                "client_id" to vimeoConfig.getClientId(),
                "client_secret" to vimeoConfig.getClientSecret(),
            ),
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresIn = response.expiresIn ?: 0,
        )
    }

    override fun refreshToken(refreshToken: String): PlatformTokenResult {
        log.debug("Vimeo OAuth 토큰 갱신")

        val response = vimeoOAuthApi.exchangeToken(
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to vimeoConfig.getClientId(),
                "client_secret" to vimeoConfig.getClientSecret(),
            ),
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresIn = response.expiresIn ?: 0,
        )
    }

    override fun deleteVideo(platformVideoId: String, accessToken: String): Boolean {
        log.info("Vimeo 영상 삭제: videoId={}", platformVideoId)

        return try {
            vimeoApi.deleteVideo(
                videoId = platformVideoId,
                authorization = "Bearer $accessToken",
            )
            true
        } catch (e: Exception) {
            log.error("Vimeo 영상 삭제 실패: {}", e.message)
            false
        }
    }

    // --- Comment API ---

    override fun getCommentCapabilities(): PlatformCommentCapabilities =
        PlatformCommentCapabilities(canListComments = true, canReply = true, canDelete = true)

    override fun listComments(
        platformVideoId: String,
        accessToken: String,
        pageToken: String?,
        maxResults: Int,
    ): PlatformCommentListResult {
        log.debug("Vimeo 댓글 조회: videoId={}", platformVideoId)

        return try {
            val page = pageToken?.toIntOrNull() ?: 1
            val response = vimeoApi.getComments(
                videoId = platformVideoId,
                perPage = maxResults.coerceAtMost(100),
                page = page,
                authorization = "Bearer $accessToken",
            )

            val comments = response.data?.mapNotNull { comment ->
                val commentId = comment.uri?.substringAfterLast("/") ?: return@mapNotNull null
                PlatformComment(
                    platformCommentId = commentId,
                    authorName = comment.user?.name ?: "Unknown",
                    authorAvatarUrl = comment.user?.pictures?.sizes?.lastOrNull()?.link,
                    authorChannelUrl = comment.user?.link,
                    content = comment.text ?: "",
                    replyCount = comment.metadata?.connections?.replies?.total ?: 0,
                    publishedAt = comment.createdOn?.let { parseIsoDateTime(it) },
                )
            } ?: emptyList()

            val totalPages = if ((response.perPage ?: 1) > 0) ((response.total ?: 0) + (response.perPage ?: 1) - 1) / (response.perPage ?: 1) else 1
            val hasMore = page < totalPages

            PlatformCommentListResult(
                comments = comments,
                nextPageToken = if (hasMore) (page + 1).toString() else null,
                totalCount = response.total,
            )
        } catch (e: Exception) {
            log.warn("Vimeo 댓글 조회 실패: {}", e.message)
            PlatformCommentListResult(emptyList())
        }
    }

    override fun replyToComment(
        platformCommentId: String,
        content: String,
        accessToken: String,
        platformVideoId: String?,
    ): PlatformCommentReplyResult {
        log.info("Vimeo 댓글 답글: videoId={}, commentId={}", platformVideoId, platformCommentId)

        return try {
            val videoId = platformVideoId ?: return PlatformCommentReplyResult("", success = false, errorMessage = "videoId required")
            val response = vimeoApi.createComment(
                videoId = videoId,
                authorization = "Bearer $accessToken",
                body = VimeoCommentRequest(text = content),
            )
            val replyId = response.uri?.substringAfterLast("/") ?: ""
            PlatformCommentReplyResult(platformCommentId = replyId, success = replyId.isNotEmpty())
        } catch (e: Exception) {
            log.error("Vimeo 댓글 답글 실패: {}", e.message)
            PlatformCommentReplyResult("", success = false, errorMessage = e.message)
        }
    }

    override fun deleteComment(
        platformCommentId: String,
        accessToken: String,
    ): PlatformCommentDeleteResult {
        log.info("Vimeo 댓글 삭제: commentId={}", platformCommentId)

        return try {
            // Need videoId to delete - use platformCommentId which contains both
            vimeoApi.deleteComment(
                videoId = platformCommentId.substringBefore(":"),
                commentId = platformCommentId.substringAfter(":"),
                authorization = "Bearer $accessToken",
            )
            PlatformCommentDeleteResult(success = true)
        } catch (e: Exception) {
            log.error("Vimeo 댓글 삭제 실패: {}", e.message)
            PlatformCommentDeleteResult(success = false, errorMessage = e.message)
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
            "PUBLIC" -> "anybody"
            "PRIVATE" -> "nobody"
            "UNLISTED" -> "unlisted"
            else -> "nobody"
        }
}
