package com.ongo.infrastructure.external.naverclip

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformUploadException
import com.ongo.infrastructure.external.naverclip.dto.NaverClipUploadCompleteRequest
import com.ongo.infrastructure.external.naverclip.dto.NaverClipUploadInitRequest
import com.ongo.infrastructure.external.platform.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class NaverClipClient(
    private val naverClipApi: NaverClipApi,
    private val naverOAuthApi: NaverOAuthApi,
    private val naverClipConfig: NaverClipConfig,
) : PlatformClient {

    private val log = LoggerFactory.getLogger(NaverClipClient::class.java)

    override val platform: Platform = Platform.NAVER_CLIP

    override fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult {
        log.info("Naver Clip 영상 업로드 시작: title={}", request.title)

        val visibility = mapVisibility(request.visibility)

        try {
            // Step 1: Initialize upload
            val initResponse = naverClipApi.initUpload(
                authorization = "Bearer ${request.accessToken}",
                request = NaverClipUploadInitRequest(
                    title = request.title.take(100),
                    description = request.description.take(1000),
                    tags = request.tags,
                    fileSize = request.fileSize,
                    visibility = visibility,
                ),
            )

            if (initResponse.error != null) {
                throw PlatformUploadException(
                    "Naver Clip",
                    "업로드 초기화 실패: ${initResponse.error.message}",
                )
            }

            val uploadId = initResponse.uploadId
                ?: throw PlatformUploadException("Naver Clip", "upload_id를 받지 못했습니다")

            log.debug("Naver Clip 업로드 초기화 완료: uploadId={}", uploadId)

            // Step 2: Upload file to the provided uploadUrl
            // In production: upload file binary to initResponse.uploadUrl

            // Step 3: Complete upload
            val completeResponse = naverClipApi.completeUpload(
                authorization = "Bearer ${request.accessToken}",
                request = NaverClipUploadCompleteRequest(uploadId = uploadId),
            )

            if (completeResponse.error != null) {
                throw PlatformUploadException(
                    "Naver Clip",
                    "업로드 완료 처리 실패: ${completeResponse.error.message}",
                )
            }

            val clipId = completeResponse.clipId
                ?: throw PlatformUploadException("Naver Clip", "clip_id를 받지 못했습니다")

            log.info("Naver Clip 업로드 완료: clipId={}", clipId)

            return PlatformUploadResult(
                platformVideoId = clipId,
                platformUrl = completeResponse.clipUrl ?: "",
                status = completeResponse.status ?: "PROCESSING",
            )
        } catch (e: PlatformUploadException) {
            throw e
        } catch (e: Exception) {
            log.error("Naver Clip 업로드 실패: {}", e.message, e)
            throw PlatformUploadException("Naver Clip", e.message ?: "알 수 없는 오류", e)
        }
    }

    override fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus {
        log.debug("Naver Clip 상태 조회: clipId={}", platformVideoId)

        try {
            val response = naverClipApi.getClipStatus(
                clipId = platformVideoId,
                authorization = "Bearer $accessToken",
            )

            if (response.error != null) {
                return PlatformVideoStatus(
                    platformVideoId = platformVideoId,
                    status = "FAILED",
                    errorMessage = response.error.message,
                )
            }

            return PlatformVideoStatus(
                platformVideoId = platformVideoId,
                status = response.status ?: "unknown",
                errorMessage = response.errorMessage,
            )
        } catch (e: Exception) {
            log.warn("Naver Clip 상태 조회 실패: {}", e.message)
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
        log.debug("Naver Clip 분석 데이터 조회: clipId={}", platformVideoId)

        try {
            val response = naverClipApi.getClipStatistics(
                clipId = platformVideoId,
                startDate = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                endDate = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                authorization = "Bearer $accessToken",
            )

            if (response.error != null) {
                log.warn("Naver Clip 분석 조회 실패: {}", response.error.message)
                return PlatformAnalytics(0, 0, 0, 0, 0, 0)
            }

            return PlatformAnalytics(
                views = response.viewCount ?: 0,
                likes = response.likeCount ?: 0,
                comments = response.commentCount ?: 0,
                shares = response.shareCount ?: 0,
                watchTimeSeconds = response.watchTimeSeconds ?: 0,
                subscriberGained = 0,
            )
        } catch (e: Exception) {
            log.warn("Naver Clip 분석 데이터 조회 실패: {}", e.message)
            return PlatformAnalytics(0, 0, 0, 0, 0, 0)
        }
    }

    override fun getChannelInfo(accessToken: String): PlatformChannelInfo {
        log.debug("Naver Clip 채널 정보 조회")

        val response = naverClipApi.getChannelInfo(
            authorization = "Bearer $accessToken",
        )

        if (response.error != null) {
            throw PlatformUploadException("Naver Clip", "채널 정보 조회 실패: ${response.error.message}")
        }

        return PlatformChannelInfo(
            channelId = response.channelId ?: "",
            channelName = response.channelName ?: "",
            channelUrl = response.channelUrl ?: "",
            subscriberCount = response.subscriberCount ?: 0,
            profileImageUrl = response.profileImageUrl,
        )
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String): PlatformTokenResult {
        log.debug("Naver OAuth 인가 코드 교환")

        val response = naverOAuthApi.refreshToken(
            mapOf(
                "grant_type" to "authorization_code",
                "client_id" to naverClipConfig.getClientId(),
                "client_secret" to naverClipConfig.getClientSecret(),
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
        log.debug("Naver OAuth 토큰 갱신")

        val response = naverOAuthApi.refreshToken(
            mapOf(
                "grant_type" to "refresh_token",
                "client_id" to naverClipConfig.getClientId(),
                "client_secret" to naverClipConfig.getClientSecret(),
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
        log.info("Naver Clip 영상 삭제: clipId={}", platformVideoId)

        return try {
            naverClipApi.deleteClip(
                clipId = platformVideoId,
                authorization = "Bearer $accessToken",
            )
            true
        } catch (e: Exception) {
            log.error("Naver Clip 영상 삭제 실패: {}", e.message)
            false
        }
    }

    private fun mapVisibility(visibility: String): String =
        when (visibility.uppercase()) {
            "PUBLIC" -> "PUBLIC"
            "PRIVATE" -> "PRIVATE"
            "UNLISTED" -> "PRIVATE" // Naver Clip doesn't have unlisted
            else -> "PRIVATE"
        }
}
