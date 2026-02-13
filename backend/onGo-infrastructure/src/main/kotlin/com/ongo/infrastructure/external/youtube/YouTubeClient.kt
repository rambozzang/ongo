package com.ongo.infrastructure.external.youtube

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformUploadException
import com.ongo.infrastructure.external.platform.*
import com.ongo.infrastructure.external.youtube.dto.YouTubeUploadRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class YouTubeClient(
    private val youTubeApi: YouTubeApi,
    private val youTubeAnalyticsApi: YouTubeAnalyticsApi,
    private val googleOAuthApi: GoogleOAuthApi,
    private val youTubeConfig: YouTubeConfig,
) : PlatformClient {

    private val log = LoggerFactory.getLogger(YouTubeClient::class.java)

    override val platform: Platform = Platform.YOUTUBE

    override fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult {
        log.info("YouTube 영상 업로드 시작: title={}", request.title)

        val privacyStatus = mapVisibility(request.visibility)

        val metadata = YouTubeUploadRequest(
            snippet = YouTubeUploadRequest.Snippet(
                title = request.title.take(100),
                description = request.description.take(5000),
                tags = request.tags,
            ),
            status = YouTubeUploadRequest.Status(
                privacyStatus = privacyStatus,
                publishAt = request.scheduledAt?.format(DateTimeFormatter.ISO_DATE_TIME),
            ),
        )

        try {
            val response = youTubeApi.initiateResumableUpload(
                authorization = "Bearer ${request.accessToken}",
                contentType = "application/json",
                metadata = metadata,
            )

            log.info("YouTube 업로드 완료: videoId={}", response.id)

            return PlatformUploadResult(
                platformVideoId = response.id,
                platformUrl = "https://www.youtube.com/watch?v=${response.id}",
                status = response.status?.uploadStatus ?: "uploaded",
            )
        } catch (e: Exception) {
            log.error("YouTube 업로드 실패: {}", e.message, e)
            throw PlatformUploadException("YouTube", e.message ?: "알 수 없는 오류", e)
        }
    }

    override fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus {
        log.debug("YouTube 영상 상태 조회: videoId={}", platformVideoId)

        val response = youTubeApi.listVideos(
            id = platformVideoId,
            part = "status,snippet",
            authorization = "Bearer $accessToken",
        )

        val video = response.items.firstOrNull()
            ?: return PlatformVideoStatus(
                platformVideoId = platformVideoId,
                status = "not_found",
                errorMessage = "영상을 찾을 수 없습니다",
            )

        val status = video.status
        val errorMessage = when {
            status?.failureReason != null -> status.failureReason
            status?.rejectionReason != null -> status.rejectionReason
            else -> null
        }

        return PlatformVideoStatus(
            platformVideoId = platformVideoId,
            status = status?.uploadStatus ?: "unknown",
            errorMessage = errorMessage,
        )
    }

    override fun getVideoAnalytics(
        platformVideoId: String,
        accessToken: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): PlatformAnalytics {
        log.debug("YouTube 분석 데이터 조회: videoId={}, {} ~ {}", platformVideoId, startDate, endDate)

        try {
            val response = youTubeAnalyticsApi.queryAnalytics(
                ids = "channel==MINE",
                startDate = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                endDate = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                metrics = "views,likes,comments,shares,estimatedMinutesWatched,subscribersGained",
                filters = "video==$platformVideoId",
                authorization = "Bearer $accessToken",
            )

            val row = response.rows?.firstOrNull()
            if (row != null && row.size >= 6) {
                return PlatformAnalytics(
                    views = row[0].toLongOrNull() ?: 0,
                    likes = row[1].toLongOrNull() ?: 0,
                    comments = row[2].toLongOrNull() ?: 0,
                    shares = row[3].toLongOrNull() ?: 0,
                    watchTimeSeconds = (row[4].toLongOrNull() ?: 0) * 60, // minutes → seconds
                    subscriberGained = row[5].toIntOrNull() ?: 0,
                )
            }

            return PlatformAnalytics(0, 0, 0, 0, 0, 0)
        } catch (e: Exception) {
            log.warn("YouTube 분석 데이터 조회 실패: {}", e.message)
            return PlatformAnalytics(0, 0, 0, 0, 0, 0)
        }
    }

    override fun getChannelInfo(accessToken: String): PlatformChannelInfo {
        log.debug("YouTube 채널 정보 조회")

        val response = youTubeApi.listChannels(
            part = "snippet,statistics",
            mine = true,
            authorization = "Bearer $accessToken",
        )

        val channel = response.items.firstOrNull()
            ?: throw PlatformUploadException("YouTube", "채널 정보를 가져올 수 없습니다")

        return PlatformChannelInfo(
            channelId = channel.id,
            channelName = channel.snippet?.title ?: "",
            channelUrl = channel.snippet?.customUrl?.let { "https://www.youtube.com/$it" } ?: "",
            subscriberCount = channel.statistics?.subscriberCount?.toLongOrNull() ?: 0,
            profileImageUrl = channel.snippet?.thumbnails?.default?.url,
        )
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String): PlatformTokenResult {
        log.debug("Google OAuth 인가 코드 교환")

        val response = googleOAuthApi.refreshToken(
            mapOf(
                "client_id" to youTubeConfig.getClientId(),
                "client_secret" to youTubeConfig.getClientSecret(),
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
        log.debug("Google OAuth 토큰 갱신")

        val response = googleOAuthApi.refreshToken(
            mapOf(
                "client_id" to youTubeConfig.getClientId(),
                "client_secret" to youTubeConfig.getClientSecret(),
                "refresh_token" to refreshToken,
                "grant_type" to "refresh_token",
            ),
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresIn = response.expiresIn,
        )
    }

    override fun deleteVideo(platformVideoId: String, accessToken: String): Boolean {
        log.info("YouTube 영상 삭제: videoId={}", platformVideoId)

        return try {
            youTubeApi.deleteVideo(
                id = platformVideoId,
                authorization = "Bearer $accessToken",
            )
            true
        } catch (e: Exception) {
            log.error("YouTube 영상 삭제 실패: {}", e.message)
            false
        }
    }

    private fun mapVisibility(visibility: String): String =
        when (visibility.uppercase()) {
            "PUBLIC" -> "public"
            "PRIVATE" -> "private"
            "UNLISTED" -> "unlisted"
            else -> "private"
        }
}
