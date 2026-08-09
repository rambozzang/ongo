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
        log.debug("Naver Clip 상태 조회: clipId={}", platformVideoId)

        try {
            val response = naverClipApi.getClipStatus(
                clipId = platformVideoId,
                authorization = "Bearer $accessToken",
            )

            if (response.error != null) {
                throw PlatformApiException("Naver Clip", "영상 상태 조회 실패: ${response.error.message}")
            }

            return PlatformVideoStatus(
                platformVideoId = platformVideoId,
                status = response.status ?: "unknown",
                errorMessage = response.errorMessage,
            )
        } catch (e: Exception) {
            log.warn("Naver Clip 상태 조회 실패: {}", e.message)
            throw PlatformApiException("Naver Clip", "영상 상태 조회 실패", e)
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
                throw PlatformApiException("Naver Clip", "분석 데이터 조회 실패: ${response.error.message}")
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
            throw PlatformApiException("Naver Clip", "분석 데이터 조회 실패", e)
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

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String, codeVerifier: String?): PlatformTokenResult {
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

    override fun revokeToken(accessToken: String): Boolean {
        log.info("Naver OAuth 토큰 폐기")
        return try {
            naverOAuthApi.revokeToken(
                grantType = "delete",
                clientId = naverClipConfig.getClientId(),
                clientSecret = naverClipConfig.getClientSecret(),
                accessToken = accessToken,
            )
            true
        } catch (e: Exception) {
            log.warn("Naver 토큰 폐기 실패: {}", e.message)
            false
        }
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

    override fun listVideos(accessToken: String, platformChannelId: String?, maxResults: Int, pageToken: String?): PlatformFeedResult {
        try {
            val page = pageToken?.toIntOrNull() ?: 0
            val response = naverClipApi.listClips(
                authorization = "Bearer $accessToken",
                page = page,
                size = maxResults,
            )
            if (response.error != null) return PlatformFeedResult(emptyList())
            val items = response.clips.map { clip ->
                PlatformFeedItem(
                    platformVideoId = clip.clipId,
                    title = clip.title ?: "",
                    thumbnailUrl = clip.thumbnailUrl,
                    platformUrl = clip.clipUrl,
                    viewCount = clip.viewCount ?: 0,
                    likeCount = clip.likeCount ?: 0,
                    commentCount = clip.commentCount ?: 0,
                    publishedAt = clip.createdAt,
                )
            }
            val nextPage = if (items.size == maxResults) (page + 1).toString() else null
            return PlatformFeedResult(
                items = items,
                nextPageToken = nextPage,
                totalCount = response.totalCount,
            )
        } catch (e: Exception) {
            log.error("Naver Clip 목록 조회 실패: {}", e.message)
            return PlatformFeedResult(emptyList())
        }
    }

    // --- Comment API ---
    // Naver Clip does not provide a public comment API.

    override fun getCommentCapabilities(): PlatformCommentCapabilities =
        PlatformCommentCapabilities()

}
