package com.ongo.infrastructure.external.pinterest

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformUploadException
import com.ongo.infrastructure.external.platform.*
import com.ongo.infrastructure.external.pinterest.dto.PinterestMediaRequest
import com.ongo.infrastructure.external.pinterest.dto.PinterestPinRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class PinterestClient(
    private val pinterestApi: PinterestApi,
    private val pinterestOAuthApi: PinterestOAuthApi,
    private val pinterestConfig: PinterestConfig,
) : PlatformClient {

    private val log = LoggerFactory.getLogger(PinterestClient::class.java)

    override val platform: Platform = Platform.PINTEREST

    override fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult {
        log.info("Pinterest 영상 업로드 시작: title={}", request.title)

        try {
            // Step 1: Register media
            val mediaResponse = pinterestApi.registerMedia(
                authorization = "Bearer ${request.accessToken}",
                request = PinterestMediaRequest(mediaType = "video"),
            )

            // Step 2: Create pin with media reference
            val pinRequest = PinterestPinRequest(
                title = request.title.take(100),
                description = request.description.take(500),
                mediaSource = PinterestPinRequest.MediaSource(
                    sourceType = "video_id",
                    mediaId = mediaResponse.mediaId,
                ),
            )

            val pinResponse = pinterestApi.createPin(
                authorization = "Bearer ${request.accessToken}",
                request = pinRequest,
            )

            log.info("Pinterest 업로드 완료: pinId={}", pinResponse.id)

            return PlatformUploadResult(
                platformVideoId = pinResponse.id,
                platformUrl = "https://www.pinterest.com/pin/${pinResponse.id}/",
                status = "published",
            )
        } catch (e: Exception) {
            log.error("Pinterest 업로드 실패: {}", e.message, e)
            throw PlatformUploadException("Pinterest", e.message ?: "알 수 없는 오류", e)
        }
    }

    override fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus {
        log.debug("Pinterest 핀 상태 조회: pinId={}", platformVideoId)

        return try {
            val response = pinterestApi.getPin(
                pinId = platformVideoId,
                authorization = "Bearer $accessToken",
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
        log.debug("Pinterest 분석 데이터 조회: pinId={}", platformVideoId)

        return try {
            val response = pinterestApi.getPinAnalytics(
                pinId = platformVideoId,
                startDate = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                endDate = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                metricTypes = "IMPRESSION,PIN_CLICK,SAVE,VIDEO_START",
                authorization = "Bearer $accessToken",
            )

            val metrics = response.all?.lifetime_metrics ?: emptyMap()

            PlatformAnalytics(
                views = metrics["IMPRESSION"] ?: 0,
                likes = metrics["SAVE"] ?: 0,
                comments = 0,
                shares = metrics["PIN_CLICK"] ?: 0,
                watchTimeSeconds = 0,
                subscriberGained = 0,
            )
        } catch (e: Exception) {
            log.warn("Pinterest 분석 데이터 조회 실패: {}", e.message)
            PlatformAnalytics(0, 0, 0, 0, 0, 0)
        }
    }

    override fun getChannelInfo(accessToken: String): PlatformChannelInfo {
        log.debug("Pinterest 사용자 정보 조회")

        val response = pinterestApi.getUserAccount(
            authorization = "Bearer $accessToken",
        )

        return PlatformChannelInfo(
            channelId = response.username ?: "",
            channelName = response.username ?: "",
            channelUrl = response.username?.let { "https://www.pinterest.com/$it/" } ?: "",
            subscriberCount = response.followerCount ?: 0,
            profileImageUrl = response.profileImage,
        )
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String): PlatformTokenResult {
        log.debug("Pinterest OAuth 인가 코드 교환")

        val response = pinterestOAuthApi.exchangeToken(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to authorizationCode,
                "redirect_uri" to redirectUri,
            ),
        )

        return PlatformTokenResult(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresIn = response.expiresIn,
        )
    }

    override fun refreshToken(refreshToken: String): PlatformTokenResult {
        log.debug("Pinterest OAuth 토큰 갱신")

        val response = pinterestOAuthApi.exchangeToken(
            mapOf(
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
        log.info("Pinterest 핀 삭제: pinId={}", platformVideoId)

        return try {
            pinterestApi.deletePin(
                pinId = platformVideoId,
                authorization = "Bearer $accessToken",
            )
            true
        } catch (e: Exception) {
            log.error("Pinterest 핀 삭제 실패: {}", e.message)
            false
        }
    }

    // --- Comment API ---
    // Pinterest API v5 does not provide comment endpoints for pins.

    override fun getCommentCapabilities(): PlatformCommentCapabilities =
        PlatformCommentCapabilities()
}
