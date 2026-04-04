package com.ongo.infrastructure.external.tiktok

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformUploadException
import com.ongo.infrastructure.external.platform.*
import com.ongo.infrastructure.external.tiktok.dto.TikTokPublishStatusRequest
import com.ongo.infrastructure.external.tiktok.dto.TikTokVideoListRequest
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

    override fun uploadVideo(request: PlatformUploadRequest): PlatformUploadResult {
        // 스트리밍 업로드가 기본이므로 이 메서드는 더 이상 사용하지 않습니다.
        // 실제 업로드는 TikTokStreamWriter (StreamPublishUseCase)에서 처리합니다.
        throw UnsupportedOperationException(
            "TikTokClient.uploadVideo()는 더 이상 사용하지 않습니다. StreamPublishUseCase를 사용하세요."
        )
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

    override fun listVideos(accessToken: String, platformChannelId: String?, maxResults: Int, pageToken: String?): PlatformFeedResult {
        try {
            val cursor = pageToken?.toLongOrNull()
            val response = tikTokApi.listVideos(
                authorization = "Bearer $accessToken",
                request = TikTokVideoListRequest(maxCount = maxResults, cursor = cursor),
            )
            val data = response.data ?: return PlatformFeedResult(emptyList())
            val items = data.videos.map { video ->
                PlatformFeedItem(
                    platformVideoId = video.id,
                    title = video.title ?: "",
                    thumbnailUrl = video.coverImageUrl,
                    viewCount = video.viewCount ?: 0,
                    likeCount = video.likeCount ?: 0,
                    commentCount = video.commentCount ?: 0,
                    shareCount = video.shareCount ?: 0,
                    publishedAt = video.createTime?.let {
                        java.time.Instant.ofEpochSecond(it).toString()
                    },
                )
            }
            return PlatformFeedResult(
                items = items,
                nextPageToken = if (data.hasMore) data.cursor?.toString() else null,
            )
        } catch (e: Exception) {
            log.error("TikTok 영상 목록 조회 실패: {}", e.message)
            return PlatformFeedResult(emptyList())
        }
    }

    // --- Comment API ---
    // TikTok API v2 only supports read-only comment listing.

    override fun getCommentCapabilities(): PlatformCommentCapabilities =
        PlatformCommentCapabilities(canListComments = true)

    override fun listComments(
        platformVideoId: String,
        accessToken: String,
        pageToken: String?,
        maxResults: Int,
    ): PlatformCommentListResult {
        log.debug("TikTok 댓글 조회: videoId={}", platformVideoId)

        return try {
            val response = tikTokApi.listComments(
                authorization = "Bearer $accessToken",
                request = com.ongo.infrastructure.external.tiktok.dto.TikTokCommentListRequest(
                    videoId = platformVideoId,
                    maxCount = maxResults.coerceAtMost(50),
                    cursor = pageToken?.toLongOrNull(),
                ),
            )

            val comments = response.data?.comments?.mapNotNull { comment ->
                PlatformComment(
                    platformCommentId = comment.id ?: return@mapNotNull null,
                    parentCommentId = comment.parentCommentId,
                    authorName = comment.user?.displayName ?: "Unknown",
                    authorAvatarUrl = comment.user?.avatarUrl,
                    authorChannelUrl = comment.user?.profileDeepLink,
                    content = comment.text ?: "",
                    likeCount = comment.likeCount ?: 0,
                    replyCount = comment.replyCount ?: 0,
                    publishedAt = comment.createTime?.let {
                        java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                    },
                )
            } ?: emptyList()

            PlatformCommentListResult(
                comments = comments,
                nextPageToken = if (response.data?.hasMore == true) response.data.cursor?.toString() else null,
            )
        } catch (e: Exception) {
            log.warn("TikTok 댓글 조회 실패: {}", e.message)
            PlatformCommentListResult(emptyList())
        }
    }

}
