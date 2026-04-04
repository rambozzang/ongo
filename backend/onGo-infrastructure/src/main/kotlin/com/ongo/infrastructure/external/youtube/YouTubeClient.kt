package com.ongo.infrastructure.external.youtube

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformUploadException
import com.ongo.infrastructure.external.platform.*
import com.ongo.infrastructure.external.youtube.dto.YouTubeCommentInsertRequest
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
        // 스트리밍 업로드가 기본이므로 이 메서드는 더 이상 사용하지 않습니다.
        // 실제 업로드는 YouTubeStreamWriter (StreamPublishUseCase)에서 처리합니다.
        throw UnsupportedOperationException(
            "YouTubeClient.uploadVideo()는 더 이상 사용하지 않습니다. StreamPublishUseCase를 사용하세요."
        )
    }

    override fun getVideoMetadata(platformVideoId: String, accessToken: String): PlatformVideoMetadata? {
        return try {
            val response = youTubeApi.listVideos(
                id = platformVideoId,
                part = "snippet,status,statistics",
                authorization = "Bearer $accessToken",
            )
            val item = response.items.firstOrNull() ?: return null
            PlatformVideoMetadata(
                title = item.snippet?.title ?: "",
                description = item.snippet?.description ?: "",
                tags = emptyList(), // YouTube snippet.tags는 별도 part 필요
                status = item.status?.uploadStatus ?: "unknown",
                viewCount = item.statistics?.viewCount?.toLongOrNull() ?: 0,
                likeCount = item.statistics?.likeCount?.toLongOrNull() ?: 0,
                commentCount = item.statistics?.commentCount?.toLongOrNull() ?: 0,
            )
        } catch (e: Exception) {
            log.warn("YouTube 영상 메타데이터 조회 실패: {}", e.message)
            null
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

    override fun updateVideoMetadata(
        platformVideoId: String,
        accessToken: String,
        title: String,
        description: String,
        tags: List<String>,
    ): Boolean {
        log.info("YouTube 영상 메타데이터 업데이트: videoId={}", platformVideoId)
        return try {
            // YouTube API는 snippet 업데이트 시 categoryId가 필수 — 기존 영상에서 조회
            val existing = youTubeApi.listVideos(
                id = platformVideoId,
                part = "snippet",
                authorization = "Bearer $accessToken",
            )
            val categoryId = existing.items?.firstOrNull()?.snippet?.categoryId ?: "22"

            val body = mapOf(
                "id" to platformVideoId,
                "snippet" to mapOf(
                    "title" to title.take(100),
                    "description" to description.take(5000),
                    "tags" to tags,
                    "categoryId" to categoryId,
                ),
            )
            youTubeApi.updateVideo(
                authorization = "Bearer $accessToken",
                body = body,
            )
            true
        } catch (e: Exception) {
            log.error("YouTube 영상 메타데이터 업데이트 실패: {}", e.message)
            false
        }
    }

    override fun listVideos(accessToken: String, platformChannelId: String?, maxResults: Int, pageToken: String?): PlatformFeedResult {
        try {
            // 1. 채널의 uploads playlist ID 조회
            val channelResponse = youTubeApi.listChannels(
                part = "contentDetails",
                mine = true,
                authorization = "Bearer $accessToken",
            )
            val uploadsPlaylistId = channelResponse.items.firstOrNull()
                ?.contentDetails?.relatedPlaylists?.get("uploads")
                ?: return PlatformFeedResult(emptyList())

            // 2. playlist items 조회
            val playlistResponse = youTubeApi.listPlaylistItems(
                playlistId = uploadsPlaylistId,
                part = "snippet,contentDetails",
                maxResults = maxResults,
                pageToken = pageToken,
                authorization = "Bearer $accessToken",
            )

            val videoIds = playlistResponse.items.mapNotNull { it.contentDetails?.videoId }
            if (videoIds.isEmpty()) return PlatformFeedResult(emptyList(), playlistResponse.nextPageToken)

            // 3. 통계 데이터 조회 (batch)
            val statsResponse = youTubeApi.listVideos(
                id = videoIds.joinToString(","),
                part = "statistics",
                authorization = "Bearer $accessToken",
            )
            val statsMap = statsResponse.items.associateBy { it.id }

            val items = playlistResponse.items.mapNotNull { item ->
                val videoId = item.contentDetails?.videoId ?: return@mapNotNull null
                val stats = statsMap[videoId]?.statistics
                PlatformFeedItem(
                    platformVideoId = videoId,
                    title = item.snippet?.title ?: "",
                    description = item.snippet?.description,
                    thumbnailUrl = item.snippet?.thumbnails?.high?.url
                        ?: item.snippet?.thumbnails?.medium?.url,
                    platformUrl = "https://www.youtube.com/watch?v=$videoId",
                    viewCount = stats?.viewCount?.toLongOrNull() ?: 0,
                    likeCount = stats?.likeCount?.toLongOrNull() ?: 0,
                    commentCount = stats?.commentCount?.toLongOrNull() ?: 0,
                    publishedAt = item.snippet?.publishedAt,
                )
            }

            return PlatformFeedResult(
                items = items,
                nextPageToken = playlistResponse.nextPageToken,
                totalCount = playlistResponse.pageInfo?.totalResults,
            )
        } catch (e: Exception) {
            log.error("YouTube 영상 목록 조회 실패: {}", e.message)
            return PlatformFeedResult(emptyList())
        }
    }

    // --- Comment API ---

    override fun getCommentCapabilities(): PlatformCommentCapabilities =
        PlatformCommentCapabilities(canListComments = true, canReply = true, canDelete = true, canHide = true)

    override fun listComments(
        platformVideoId: String,
        accessToken: String,
        pageToken: String?,
        maxResults: Int,
    ): PlatformCommentListResult {
        log.debug("YouTube 댓글 조회: videoId={}", platformVideoId)

        val response = youTubeApi.listCommentThreads(
            videoId = platformVideoId,
            part = "snippet",
            maxResults = maxResults.coerceAtMost(100),
            pageToken = pageToken,
            order = "time",
            authorization = "Bearer $accessToken",
        )

        val comments = response.items.mapNotNull { thread ->
            val snippet = thread.snippet?.topLevelComment?.snippet ?: return@mapNotNull null
            PlatformComment(
                platformCommentId = thread.snippet.topLevelComment.id ?: thread.id,
                authorName = snippet.authorDisplayName ?: "Unknown",
                authorAvatarUrl = snippet.authorProfileImageUrl,
                authorChannelUrl = snippet.authorChannelUrl,
                content = snippet.textDisplay ?: "",
                likeCount = snippet.likeCount ?: 0,
                replyCount = thread.snippet.totalReplyCount ?: 0,
                publishedAt = snippet.publishedAt?.let { parseIsoDateTime(it) },
            )
        }

        return PlatformCommentListResult(
            comments = comments,
            nextPageToken = response.nextPageToken,
            totalCount = response.pageInfo?.totalResults,
        )
    }

    override fun replyToComment(
        platformCommentId: String,
        content: String,
        accessToken: String,
        platformVideoId: String?,
    ): PlatformCommentReplyResult {
        log.info("YouTube 댓글 답글: parentId={}", platformCommentId)

        val response = youTubeApi.insertComment(
            authorization = "Bearer $accessToken",
            body = YouTubeCommentInsertRequest(
                snippet = YouTubeCommentInsertRequest.Snippet(
                    parentId = platformCommentId,
                    textOriginal = content,
                ),
            ),
        )

        return PlatformCommentReplyResult(
            platformCommentId = response.id ?: "",
            success = response.id != null,
        )
    }

    override fun deleteComment(
        platformCommentId: String,
        accessToken: String,
    ): PlatformCommentDeleteResult {
        log.info("YouTube 댓글 삭제: commentId={}", platformCommentId)

        return try {
            youTubeApi.deleteComment(
                id = platformCommentId,
                authorization = "Bearer $accessToken",
            )
            PlatformCommentDeleteResult(success = true)
        } catch (e: Exception) {
            log.error("YouTube 댓글 삭제 실패: {}", e.message)
            PlatformCommentDeleteResult(success = false, errorMessage = e.message)
        }
    }

    private fun parseIsoDateTime(iso: String): java.time.LocalDateTime? =
        try {
            java.time.OffsetDateTime.parse(iso).toLocalDateTime()
        } catch (_: Exception) {
            null
        }

}
