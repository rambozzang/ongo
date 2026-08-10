package com.ongo.infrastructure.external.naverclip

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformApiException
import com.ongo.common.exception.PlatformUploadException
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
        // 스트리밍 업로드가 기본이므로 이 메서드는 더 이상 사용하지 않습니다.
        // 실제 업로드는 NaverClipStreamWriter (StreamPublishUseCase)에서 처리합니다.
        throw UnsupportedOperationException(
            "NaverClipClient.uploadVideo()는 더 이상 사용하지 않습니다. StreamPublishUseCase를 사용하세요."
        )
    }

    override fun getVideoStatus(platformVideoId: String, accessToken: String): PlatformVideoStatus {
        throw unsupported()
    }

    override fun getVideoAnalytics(
        platformVideoId: String,
        accessToken: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): PlatformAnalytics {
        log.debug("Naver Clip 분석 데이터 조회: clipId={}", platformVideoId)

        throw unsupported()
    }

    override fun getChannelInfo(accessToken: String): PlatformChannelInfo {
        log.debug("Naver Clip 채널 정보 조회")

        throw unsupported()
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String, codeVerifier: String?): PlatformTokenResult {
        throw unsupported()
    }

    override fun refreshToken(refreshToken: String): PlatformTokenResult {
        throw unsupported()
    }

    override fun revokeToken(accessToken: String): Boolean {
        log.info("Naver OAuth 토큰 폐기")
        return false
    }

    override fun deleteVideo(platformVideoId: String, accessToken: String): Boolean {
        log.info("Naver Clip 영상 삭제: clipId={}", platformVideoId)

        return false
    }

    override fun listVideos(accessToken: String, platformChannelId: String?, maxResults: Int, pageToken: String?): PlatformFeedResult {
        throw unsupported()
    }

    // --- Comment API ---
    // Naver Clip does not provide a public comment API.

    override fun getCommentCapabilities(): PlatformCommentCapabilities =
        PlatformCommentCapabilities()

    private fun unsupported(): PlatformApiException = PlatformApiException(
        "Naver Clip",
        "Naver Clip은 공개 업로드·관리 API를 제공하지 않습니다.",
    )

}
