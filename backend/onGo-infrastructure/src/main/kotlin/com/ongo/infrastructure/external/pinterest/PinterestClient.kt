package com.ongo.infrastructure.external.pinterest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformApiException
import com.ongo.common.exception.PlatformUploadException
import com.ongo.infrastructure.external.platform.*
import com.ongo.infrastructure.external.pinterest.dto.PinterestMediaRequest
import com.ongo.infrastructure.external.pinterest.dto.PinterestPinRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import org.springframework.util.LinkedMultiValueMap
import kotlin.io.path.deleteIfExists

@Component
class PinterestClient(
    private val pinterestApi: PinterestApi,
    private val pinterestOAuthApi: PinterestOAuthApi,
    private val pinterestConfig: PinterestConfig,
    private val fileTransferHelper: PlatformFileTransferHelper,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : PlatformClient {

    private val log = LoggerFactory.getLogger(PinterestClient::class.java)

    override val platform: Platform = Platform.PINTEREST

    override fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult {
        log.info("Pinterest 영상 업로드 시작: title={}", request.title)

        try {
            // Step 1: Register media
            val mediaResponse = pinterestApi.registerMedia(
                authorization = "Bearer ${request.accessToken.value}",
                request = PinterestMediaRequest(mediaType = "video"),
            )

            val uploadUrl = mediaResponse.uploadUrl
                ?: throw PlatformUploadException("Pinterest", "미디어 업로드 URL을 받지 못했습니다")
            val uploadParameters = mediaResponse.uploadParameters
                ?: throw PlatformUploadException("Pinterest", "미디어 업로드 파라미터를 받지 못했습니다")
            val thumbnailUrl = request.thumbnailUrl?.takeIf { it.isNotBlank() }
                ?: throw PlatformUploadException("Pinterest", "동영상 Pin에는 커버 이미지가 필요합니다")

            // Step 2: Upload the binary to Pinterest's signed storage URL.
            val tempFile = downloadFileToTemp(request.fileUrl)
            try {
                fileTransferHelper.uploadMultipartToPinterest(uploadUrl, uploadParameters, tempFile)
            } finally {
                tempFile.toPath().deleteIfExists()
            }

            // Step 3: Pinterest processes the uploaded media asynchronously.
            awaitMediaSucceeded(mediaResponse.mediaId, request.accessToken.value)

            // Step 4: Create pin with the ready media reference.
            val settings = request.customSettingsJson
                ?.let { runCatching { objectMapper.readTree(it) }.getOrNull() }
            val boardId = settings?.path("board")?.asText(null)
                ?.takeIf(String::isNotBlank)
                ?: request.platformChannelId
            val pinRequest = PinterestPinRequest(
                title = settings?.path("title")?.asText(null)?.takeIf(String::isNotBlank)
                    ?.take(100)
                    ?: request.title.take(100),
                description = request.description.take(800),
                link = settings?.path("link")?.asText(null)?.takeIf(String::isNotBlank),
                boardId = boardId,
                dominantColor = settings?.path("dominant_color")?.asText(null)?.takeIf(String::isNotBlank),
                mediaSource = PinterestPinRequest.MediaSource(
                    sourceType = "video_id",
                    mediaId = mediaResponse.mediaId,
                    coverImageUrl = thumbnailUrl,
                ),
            )

            val pinResponse = pinterestApi.createPin(
                authorization = "Bearer ${request.accessToken.value}",
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

    private fun awaitMediaSucceeded(mediaId: String, accessToken: String) {
        val attempts = pinterestConfig.getMediaPollAttempts().coerceAtLeast(1)
        repeat(attempts) { attempt ->
            val status = pinterestApi.getMediaStatus(
                mediaId = mediaId,
                authorization = "Bearer $accessToken",
            ).status?.lowercase()

            when (status) {
                "succeeded", "success", "ready", "finished", "completed" -> return
                "failed", "error" -> throw PlatformUploadException("Pinterest", "미디어 처리 실패")
            }

            if (attempt < attempts - 1) {
                Thread.sleep(pinterestConfig.getMediaPollIntervalMillis().coerceAtLeast(0))
            }
        }
        throw PlatformUploadException("Pinterest", "미디어 처리 시간이 초과되었습니다")
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
            throw PlatformApiException("Pinterest", "핀 상태 조회 실패", e)
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
            throw PlatformApiException("Pinterest", "분석 데이터 조회 실패", e)
        }
    }

    override fun getChannelInfo(accessToken: String): PlatformChannelInfo {
        log.debug("Pinterest 사용자 정보 조회")

        val response = pinterestApi.getUserAccount(
            authorization = "Bearer $accessToken",
        )
        val board = pinterestApi.listBoards(
            pageSize = 1,
            authorization = "Bearer $accessToken",
        ).items.firstOrNull()
            ?: throw PlatformUploadException("Pinterest", "게시할 Pinterest 보드가 없습니다")

        return PlatformChannelInfo(
            // Pinterest video Pins require board_id. Store the first owned board as
            // the channel target so a connected channel can publish valid Pins.
            channelId = board.id,
            channelName = listOfNotNull(response.username, board.name).joinToString(" · "),
            channelUrl = board.url ?: response.username?.let { "https://www.pinterest.com/$it/" } ?: "",
            subscriberCount = response.followerCount ?: 0,
            profileImageUrl = response.profileImage,
        )
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String, codeVerifier: String?): PlatformTokenResult {
        log.debug("Pinterest OAuth 인가 코드 교환")

        val response = pinterestOAuthApi.exchangeToken(
            authorization = pinterestBasicAuthorization(),
            body = LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("code", authorizationCode)
                add("redirect_uri", redirectUri)
                add("continuous_refresh", "true")
            },
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
            authorization = pinterestBasicAuthorization(),
            body = LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "refresh_token")
                add("refresh_token", refreshToken)
                add("scope", "boards:read,boards:write,pins:read,pins:write")
            },
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

    private fun pinterestBasicAuthorization(): String {
        val credentials = "${pinterestConfig.getAppId()}:${pinterestConfig.getAppSecret()}"
        return "Basic ${Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))}"
    }

    // --- Comment API ---
    // Pinterest API v5 does not provide comment endpoints for pins.

    override fun getCommentCapabilities(): PlatformCommentCapabilities =
        PlatformCommentCapabilities()
}
