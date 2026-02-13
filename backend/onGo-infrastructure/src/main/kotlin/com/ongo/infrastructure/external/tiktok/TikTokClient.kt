package com.ongo.infrastructure.external.tiktok

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformUploadException
import com.ongo.infrastructure.external.platform.*
import com.ongo.infrastructure.external.tiktok.dto.TikTokInitUploadRequest
import com.ongo.infrastructure.external.tiktok.dto.TikTokPublishStatusRequest
import com.ongo.infrastructure.external.tiktok.dto.TikTokVideoQueryRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class TikTokClient(
    private val tikTokApi: TikTokApi,
    private val tikTokOAuthApi: TikTokOAuthApi,
    private val tikTokConfig: TikTokConfig,
) : PlatformClient {

    private val log = LoggerFactory.getLogger(TikTokClient::class.java)

    override val platform: Platform = Platform.TIKTOK

    companion object {
        private const val DEFAULT_CHUNK_SIZE = 10_000_000L // 10MB
    }

    override fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult {
        log.info("TikTok 영상 업로드 시작: title={}", request.title)

        val privacyLevel = mapVisibility(request.visibility)
        val videoSize = request.fileSize

        val initRequest = TikTokInitUploadRequest(
            postInfo = TikTokInitUploadRequest.PostInfo(
                title = request.title.take(150),
                privacyLevel = privacyLevel,
            ),
            sourceInfo = TikTokInitUploadRequest.SourceInfo(
                source = "FILE_UPLOAD",
                videoSize = videoSize,
                chunkSize = DEFAULT_CHUNK_SIZE,
                totalChunkCount = maxOf(1, ((videoSize + DEFAULT_CHUNK_SIZE - 1) / DEFAULT_CHUNK_SIZE).toInt()),
            ),
        )

        try {
            val initResponse = tikTokApi.initVideoUpload(
                authorization = "Bearer ${request.accessToken}",
                request = initRequest,
            )

            if (initResponse.error != null) {
                throw PlatformUploadException(
                    "TikTok",
                    "업로드 초기화 실패: ${initResponse.error.message}",
                )
            }

            val publishId = initResponse.data?.publishId
                ?: throw PlatformUploadException("TikTok", "publish_id를 받지 못했습니다")

            // In production: upload video chunks to initResponse.data.uploadUrl
            // Then poll for publish status

            log.info("TikTok 업로드 초기화 완료: publishId={}", publishId)

            return PlatformUploadResult(
                platformVideoId = publishId,
                platformUrl = "", // URL available after publish completes
                status = "PROCESSING_UPLOAD",
            )
        } catch (e: PlatformUploadException) {
            throw e
        } catch (e: Exception) {
            log.error("TikTok 업로드 실패: {}", e.message, e)
            throw PlatformUploadException("TikTok", e.message ?: "알 수 없는 오류", e)
        }
    }

    override fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus {
        log.debug("TikTok 영상 상태 조회: publishId={}", platformVideoId)

        try {
            val response = tikTokApi.fetchPublishStatus(
                authorization = "Bearer $accessToken",
                request = TikTokPublishStatusRequest(publishId = platformVideoId),
            )

            if (response.error != null) {
                return PlatformVideoStatus(
                    platformVideoId = platformVideoId,
                    status = "FAILED",
                    errorMessage = response.error.message,
                )
            }

            val status = response.data?.status ?: "unknown"
            val errorMessage = response.data?.failReason

            return PlatformVideoStatus(
                platformVideoId = platformVideoId,
                status = status,
                errorMessage = errorMessage,
            )
        } catch (e: Exception) {
            log.warn("TikTok 상태 조회 실패: {}", e.message)
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
        log.debug("TikTok 분석 데이터 조회: videoId={}", platformVideoId)

        try {
            val response = tikTokApi.queryVideos(
                authorization = "Bearer $accessToken",
                request = TikTokVideoQueryRequest(
                    filters = TikTokVideoQueryRequest.Filters(ids = listOf(platformVideoId)),
                    fields = listOf("id", "view_count", "like_count", "comment_count", "share_count"),
                ),
            )

            val video = response.data?.videos?.firstOrNull()
                ?: return PlatformAnalytics(0, 0, 0, 0, 0, 0)

            return PlatformAnalytics(
                views = video.viewCount ?: 0,
                likes = video.likeCount ?: 0,
                comments = video.commentCount ?: 0,
                shares = video.shareCount ?: 0,
                watchTimeSeconds = 0, // TikTok doesn't expose watch time via this endpoint
                subscriberGained = 0,
            )
        } catch (e: Exception) {
            log.warn("TikTok 분석 데이터 조회 실패: {}", e.message)
            return PlatformAnalytics(0, 0, 0, 0, 0, 0)
        }
    }

    override fun getChannelInfo(accessToken: String): PlatformChannelInfo {
        log.debug("TikTok 크리에이터 정보 조회")

        val response = tikTokApi.getCreatorInfo(
            authorization = "Bearer $accessToken",
        )

        if (response.error != null) {
            throw PlatformUploadException("TikTok", "크리에이터 정보 조회 실패: ${response.error.message}")
        }

        val data = response.data
            ?: throw PlatformUploadException("TikTok", "크리에이터 정보가 없습니다")

        return PlatformChannelInfo(
            channelId = data.creatorUsername ?: "",
            channelName = data.creatorNickname ?: data.creatorUsername ?: "",
            channelUrl = data.creatorUsername?.let { "https://www.tiktok.com/@$it" } ?: "",
            subscriberCount = data.followerCount ?: 0,
            profileImageUrl = data.creatorAvatarUrl,
        )
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String): PlatformTokenResult {
        log.debug("TikTok OAuth 인가 코드 교환")

        val response = tikTokOAuthApi.refreshToken(
            mapOf(
                "client_key" to tikTokConfig.getClientKey(),
                "client_secret" to tikTokConfig.getClientSecret(),
                "code" to authorizationCode,
                "redirect_uri" to redirectUri,
                "grant_type" to "authorization_code",
            ),
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresIn = response.expiresIn,
        )
    }

    override fun refreshToken(refreshToken: String): PlatformTokenResult {
        log.debug("TikTok OAuth 토큰 갱신")

        val response = tikTokOAuthApi.refreshToken(
            mapOf(
                "client_key" to tikTokConfig.getClientKey(),
                "client_secret" to tikTokConfig.getClientSecret(),
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
            ),
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresIn = response.expiresIn,
        )
    }

    override fun deleteVideo(platformVideoId: String, accessToken: String): Boolean {
        log.info("TikTok 영상 삭제: videoId={}", platformVideoId)
        // TikTok Content Posting API does not support video deletion.
        // Deletion must be done by the user through the TikTok app.
        log.warn("TikTok API는 영상 삭제를 지원하지 않습니다")
        return false
    }

    private fun mapVisibility(visibility: String): String =
        when (visibility.uppercase()) {
            "PUBLIC" -> "PUBLIC_TO_EVERYONE"
            "PRIVATE" -> "SELF_ONLY"
            "UNLISTED" -> "MUTUAL_FOLLOW_FRIENDS"
            else -> "SELF_ONLY"
        }
}
