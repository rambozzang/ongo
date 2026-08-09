package com.ongo.infrastructure.external.dailymotion

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformUploadException
import com.ongo.infrastructure.external.platform.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.infrastructure.external.dailymotion.dto.DailymotionCreateVideoRequest
import org.springframework.util.LinkedMultiValueMap
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class DailymotionClient(
    private val dailymotionApi: DailymotionApi,
    private val dailymotionOAuthApi: DailymotionOAuthApi,
    private val dailymotionConfig: DailymotionConfig,
    private val objectMapper: ObjectMapper,
    private val fileTransferHelper: PlatformFileTransferHelper,
) : PlatformClient {

    private val log = LoggerFactory.getLogger(DailymotionClient::class.java)

    override val platform: Platform = Platform.DAILYMOTION

    override fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult {
        log.info("Dailymotion 영상 업로드 시작: title={}", request.title)

        val profileId = request.platformChannelId
            ?: throw PlatformUploadException("Dailymotion", "프로필 ID가 필요합니다")
        val sourceFile = downloadFileToTemp(request.fileUrl)
        try {
            // API v2: create a resumable upload session.
            val uploadSession = dailymotionApi.createUploadSession(
                authorization = "Bearer ${request.accessToken}",
            )

            // The signed upload URL does not accept the platform Bearer token.
            val uploadBody = fileTransferHelper.uploadMultipartFile(
                uploadUrl = uploadSession.uploadUrl,
                file = sourceFile,
            )
            val uploadedUrl = objectMapper.readTree(uploadBody).path("url").asText(null)
                ?: throw PlatformUploadException("Dailymotion", "업로드 서버 응답에 파일 URL이 없습니다")

            val response = dailymotionApi.createVideo(
                profileId = profileId,
                authorization = "Bearer ${request.accessToken}",
                request = DailymotionCreateVideoRequest(
                    title = request.title.trim().take(255),
                    description = request.description.trim().take(3000).ifBlank { null },
                    visibility = mapVisibility(request.visibility),
                    tags = request.tags.map { it.removePrefix("#").trim() }.filter(String::isNotBlank).take(150),
                    source = DailymotionCreateVideoRequest.Source(uploadedUrl),
                ),
            )
            val videoId = response.videoId ?: response.id
                ?: throw PlatformUploadException("Dailymotion", "영상 생성 응답에 ID가 없습니다")

            log.info("Dailymotion 업로드 완료: videoId={}", videoId)

            return PlatformUploadResult(
                platformVideoId = videoId,
                platformUrl = response.url ?: "https://www.dailymotion.com/video/$videoId",
                status = response.status ?: "processing",
            )
        } catch (e: Exception) {
            log.error("Dailymotion 업로드 실패: {}", e.message, e)
            throw PlatformUploadException("Dailymotion", e.message ?: "알 수 없는 오류", e)
        } finally {
            sourceFile.delete()
        }
    }

    override fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus {
        log.debug("Dailymotion 영상 상태 조회: videoId={}", platformVideoId)

        return try {
            val response = dailymotionApi.getVideo(
                videoId = platformVideoId,
                fields = "id,status,title,url",
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
        log.debug("Dailymotion 분석 데이터 조회: videoId={}", platformVideoId)

        return try {
            val response = dailymotionApi.getVideo(
                videoId = platformVideoId,
                fields = "id,views_total,likes_total,comments_total,bookmarks_total",
                authorization = "Bearer $accessToken",
            )

            PlatformAnalytics(
                views = response.viewsTotal ?: 0,
                likes = response.likesTotal ?: 0,
                comments = response.commentsTotal ?: 0,
                shares = response.bookmarksTotal ?: 0,
                watchTimeSeconds = 0,
                subscriberGained = 0,
            )
        } catch (e: Exception) {
            log.warn("Dailymotion 분석 데이터 조회 실패: {}", e.message)
            PlatformAnalytics(0, 0, 0, 0, 0, 0)
        }
    }

    override fun getChannelInfo(accessToken: String): PlatformChannelInfo {
        log.debug("Dailymotion 사용자 정보 조회")

        val response = dailymotionApi.getUser(
            fields = "user_id,display_name,username,id,screenname,url,avatar_720_url,followers_total",
            authorization = "Bearer $accessToken",
        )
        val profileId = response.userId ?: response.id
            ?: throw PlatformUploadException("Dailymotion", "프로필 ID를 가져올 수 없습니다")

        return PlatformChannelInfo(
            channelId = profileId,
            channelName = response.displayName ?: response.screenname ?: "",
            channelUrl = response.url ?: "https://www.dailymotion.com/$profileId",
            subscriberCount = response.followersTotal ?: 0,
            profileImageUrl = response.avatarUrl,
        )
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String, codeVerifier: String?): PlatformTokenResult {
        log.debug("Dailymotion OAuth 인가 코드 교환")

        val response = dailymotionOAuthApi.exchangeToken(
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("client_id", dailymotionConfig.getApiKey())
                add("client_secret", dailymotionConfig.getApiSecret())
                add("code", authorizationCode)
                add("redirect_uri", redirectUri)
            },
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresIn = response.expiresIn,
        )
    }

    override fun refreshToken(refreshToken: String): PlatformTokenResult {
        log.debug("Dailymotion OAuth 토큰 갱신")

        val response = dailymotionOAuthApi.exchangeToken(
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "refresh_token")
                add("client_id", dailymotionConfig.getApiKey())
                add("client_secret", dailymotionConfig.getApiSecret())
                add("refresh_token", refreshToken)
            },
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresIn = response.expiresIn,
        )
    }

    override fun deleteVideo(platformVideoId: String, accessToken: String): Boolean {
        log.info("Dailymotion 영상 삭제: videoId={}", platformVideoId)

        return try {
            dailymotionApi.deleteVideo(
                videoId = platformVideoId,
                authorization = "Bearer $accessToken",
            )
            true
        } catch (e: Exception) {
            log.error("Dailymotion 영상 삭제 실패: {}", e.message)
            false
        }
    }

    private fun mapVisibility(visibility: String): String =
        when (visibility.uppercase()) {
            "PUBLIC" -> "public"
            "PASSWORD" -> "password"
            else -> "private"
        }

    // --- Comment API ---

    override fun getCommentCapabilities(): PlatformCommentCapabilities =
        PlatformCommentCapabilities(canListComments = true, canReply = true, canDelete = true)

    override fun listComments(
        platformVideoId: String,
        accessToken: String,
        pageToken: String?,
        maxResults: Int,
        publishedAfter: java.time.LocalDateTime?,
    ): PlatformCommentListResult {
        log.debug("Dailymotion 댓글 조회: videoId={}", platformVideoId)

        return try {
            val page = pageToken?.toIntOrNull() ?: 1
            val response = dailymotionApi.getComments(
                videoId = platformVideoId,
                fields = "id,message,owner.screenname,owner.avatar_60_url,owner.url,created_time",
                limit = maxResults.coerceAtMost(100),
                page = page,
                authorization = "Bearer $accessToken",
            )

            val comments = response.list?.mapNotNull { comment ->
                PlatformComment(
                    platformCommentId = comment.id ?: return@mapNotNull null,
                    authorName = comment.ownerScreenname ?: "Unknown",
                    authorAvatarUrl = comment.ownerAvatarUrl,
                    authorChannelUrl = comment.ownerUrl,
                    content = comment.message ?: "",
                    publishedAt = comment.createdTime?.let {
                        java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                    },
                )
            } ?: emptyList()

            PlatformCommentListResult(
                comments = comments,
                nextPageToken = if (response.hasMore == true) (page + 1).toString() else null,
                totalCount = response.total,
            )
        } catch (e: Exception) {
            log.warn("Dailymotion 댓글 조회 실패: {}", e.message)
            PlatformCommentListResult(emptyList())
        }
    }

    override fun replyToComment(
        platformCommentId: String,
        content: String,
        accessToken: String,
        platformVideoId: String?,
    ): PlatformCommentReplyResult {
        log.info("Dailymotion 댓글 답글: videoId={}", platformVideoId)

        return try {
            val videoId = platformVideoId ?: return PlatformCommentReplyResult("", success = false, errorMessage = "videoId required")
            val response = dailymotionApi.createComment(
                videoId = videoId,
                message = content,
                authorization = "Bearer $accessToken",
            )
            PlatformCommentReplyResult(
                platformCommentId = response.id ?: "",
                success = response.id != null,
            )
        } catch (e: Exception) {
            log.error("Dailymotion 댓글 답글 실패: {}", e.message)
            PlatformCommentReplyResult("", success = false, errorMessage = e.message)
        }
    }

    override fun deleteComment(
        platformCommentId: String,
        accessToken: String,
    ): PlatformCommentDeleteResult {
        log.info("Dailymotion 댓글 삭제: commentId={}", platformCommentId)

        return try {
            dailymotionApi.deleteComment(
                commentId = platformCommentId,
                authorization = "Bearer $accessToken",
            )
            PlatformCommentDeleteResult(success = true)
        } catch (e: Exception) {
            log.error("Dailymotion 댓글 삭제 실패: {}", e.message)
            PlatformCommentDeleteResult(success = false, errorMessage = e.message)
        }
    }
}
