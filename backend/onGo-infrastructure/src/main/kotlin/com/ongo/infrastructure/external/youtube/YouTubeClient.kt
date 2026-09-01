package com.ongo.infrastructure.external.youtube

import com.ongo.common.enums.Platform
import com.ongo.common.exception.PlatformApiException
import com.ongo.common.exception.PlatformUploadException
import com.ongo.domain.analytics.RevenueMeasurement
import com.ongo.domain.analytics.RevenueReport
import com.ongo.infrastructure.external.platform.*
import com.ongo.infrastructure.external.youtube.dto.YouTubeCommentInsertRequest
import com.ongo.application.publicapi.PlatformToolDefinition
import com.ongo.application.publicapi.PlatformToolField
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientResponseException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class YouTubeClient(
    private val youTubeApi: YouTubeApi,
    private val youTubeAnalyticsApi: YouTubeAnalyticsApi,
    private val googleOAuthApi: GoogleOAuthApi,
    private val youTubeConfig: YouTubeConfig,
) : PlatformClient {

    private companion object {
        const val REVENUE_METRIC = "estimatedRevenue"

        /**
         * 수익을 원화로 받아 온다. 지정하지 않으면 채널의 AdSense 지급 통화(USD 등)로
         * 내려오는데, `analytics_daily.revenue_micro` 는 통화 하나만 담을 수 있어
         * 원화로 읽으면 몇백 배 틀린다.
         */
        const val REVENUE_CURRENCY = "KRW"
    }

    private val log = LoggerFactory.getLogger(YouTubeClient::class.java)

    override val platform: Platform = Platform.YOUTUBE

    override fun integrationTools(): List<PlatformToolDefinition> = super.integrationTools() + listOf(
        PlatformToolDefinition(
            methodName = "listVideos",
            description = "List videos visible on the connected YouTube channel",
            dataSchema = listOf(
                PlatformToolField("maxResults", "integer", "Number of videos to return, 1 to 50"),
                PlatformToolField("pageToken", "string", "Optional YouTube continuation token"),
            ),
        ),
    )

    override fun invokeIntegrationTool(
        accessToken: String,
        platformChannelId: String?,
        methodName: String,
        data: Map<String, Any?>,
    ): Any? = when (methodName) {
        "listVideos" -> listVideos(
            accessToken = accessToken,
            platformChannelId = platformChannelId,
            maxResults = (data["maxResults"] as? Number)?.toInt()?.coerceIn(1, 50) ?: 20,
            pageToken = data["pageToken"] as? String,
        )
        else -> super.invokeIntegrationTool(accessToken, platformChannelId, methodName, data)
    }

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

        val uploadStatus = status?.uploadStatus?.uppercase() ?: "UNKNOWN"
        val privacyStatus = status?.privacyStatus?.lowercase()
        val publicStatus = when {
            uploadStatus == "PROCESSED" && privacyStatus in setOf("public", "unlisted") -> "PUBLISHED"
            uploadStatus == "FAILED" || uploadStatus == "REJECTED" -> uploadStatus
            else -> "PROCESSING"
        }

        return PlatformVideoStatus(
            platformVideoId = platformVideoId,
            status = publicStatus,
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
                metrics = "views,likes,comments,shares,estimatedMinutesWatched,subscribersGained,impressions,averageViewDuration",
                filters = "video==$platformVideoId",
                authorization = "Bearer $accessToken",
            )

            val row = response.rows?.firstOrNull()
            if (row != null && row.size >= 8) {
                return PlatformAnalytics(
                    // YouTube 는 여덟 지표를 모두 조회한다(위 metrics 목록). 즉 전부
                    // "지원" 이므로 빈 칸·파싱 실패는 미수집이 아니라 응답 이상이다.
                    views = row[0].requireLongMetric("YouTube", "views"),
                    likes = row[1].requireLongMetric("YouTube", "likes"),
                    comments = row[2].requireLongMetric("YouTube", "comments"),
                    shares = row[3].requireLongMetric("YouTube", "shares"),
                    // 분 단위로 온다.
                    watchTimeSeconds = row[4].requireLongMetric("YouTube", "estimatedMinutesWatched") * 60,
                    subscriberGained = row[5].requireIntMetric("YouTube", "subscribersGained"),
                    impressions = row[6].requireLongMetric("YouTube", "impressions"),
                    avgViewDurationSeconds = row[7].requireLongMetric("YouTube", "averageViewDuration"),
                )
            }

            throw PlatformApiException("YouTube", "분석 데이터가 없습니다.")
        } catch (e: PlatformApiException) {
            // 어떤 지표가 빠졌는지 담은 메시지를 그대로 올려 보낸다. 일반 메시지로 덮으면
            // 스케줄러 경고 로그에서 원인 지표를 알 수 없다.
            throw e
        } catch (e: Exception) {
            log.warn("YouTube 분석 데이터 조회 실패: {}", e.message)
            throw PlatformApiException("YouTube", "분석 데이터 조회 실패", e)
        }
    }

    /**
     * 일별 광고 수익. **[getVideoAnalytics] 와 분리된 호출이다.**
     *
     * 금전 지표는 `yt-analytics-monetary.readonly` 를 따로 요구한다. 기존 scope 로 이미
     * 연결된 채널은 그 권한이 없어 401/403 이 온다. 그때 예외를 밖으로 던지지 않고
     * `PERMISSION_REQUIRED` 상태만 돌려준다 — 이 호출의 실패가 같은 주기의 일반 분석
     * 수집을 멈추면 안 된다.
     *
     * 응답에 없는 날짜는 채우지 않는다. YouTube 는 확정 전 날짜의 행을 아예 주지 않으며,
     * 그걸 0 원으로 저장하면 며칠 뒤 확정될 금액을 "0 원 확정"으로 굳혀 버린다.
     */
    override fun getVideoRevenue(
        platformVideoId: String,
        accessToken: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): RevenueReport {
        return try {
            val response = youTubeAnalyticsApi.queryRevenue(
                ids = "channel==MINE",
                startDate = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                endDate = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                metrics = REVENUE_METRIC,
                dimensions = "day",
                filters = "video==$platformVideoId",
                currency = REVENUE_CURRENCY,
                authorization = "Bearer $accessToken",
            )

            val rows = response.rows
            if (rows.isNullOrEmpty()) {
                // 권한은 통과했는데 행이 없다. 확정 지연이지 0 원 확정이 아니다.
                return RevenueReport.PENDING
            }

            val daily = rows.mapNotNull { row ->
                if (row.size < 2) return@mapNotNull null
                val date = runCatching { LocalDate.parse(row[0]) }.getOrNull() ?: return@mapNotNull null
                date to RevenueMeasurement.fromApi(row[1], REVENUE_CURRENCY)
            }.toMap()

            if (daily.isEmpty()) RevenueReport.ERROR else RevenueReport.measured(daily)
        } catch (e: RestClientResponseException) {
            val status = e.statusCode.value()
            if (status == 401 || status == 403) {
                log.info(
                    "YouTube 수익 조회 권한이 없다. 채널 재연동이 필요하다: videoId={}, status={}",
                    platformVideoId, status,
                )
                RevenueReport.PERMISSION_REQUIRED
            } else {
                log.warn("YouTube 수익 조회 실패: videoId={}, status={}", platformVideoId, status)
                RevenueReport.ERROR
            }
        } catch (e: Exception) {
            log.warn("YouTube 수익 조회 실패: videoId={}, {}", platformVideoId, e.message)
            RevenueReport.ERROR
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
            subscriberCount = channel.statistics?.subscriberCount?.toLongOrNull(),
            profileImageUrl = channel.snippet?.thumbnails?.default?.url,
        )
    }

    override fun exchangeCodeForTokens(authorizationCode: String, redirectUri: String, codeVerifier: String?): PlatformTokenResult {
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

    override fun revokeToken(accessToken: String): Boolean {
        log.info("Google OAuth 토큰 폐기")
        return try {
            googleOAuthApi.revokeToken(accessToken)
            true
        } catch (e: Exception) {
            log.warn("Google 토큰 폐기 실패: {}", e.message)
            false
        }
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
                ?: return PlatformFeedResult(
                    items = emptyList(),
                    errorMessage = "YouTube 채널의 업로드 목록을 확인하지 못했습니다.",
                )

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
                    // 비공개·통계 숨김 영상은 이 값들이 응답에서 빠진다 — 0 이 아니라 미측정이다.
                    // 공유 수는 videos.list statistics 가 주지 않으므로 기본값(null)로 남는다.
                    viewCount = stats?.viewCount?.toLongOrNull(),
                    likeCount = stats?.likeCount?.toLongOrNull(),
                    commentCount = stats?.commentCount?.toLongOrNull(),
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
            return PlatformFeedResult(
                items = emptyList(),
                errorMessage = "YouTube 영상 목록을 불러오지 못했습니다.",
            )
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
        publishedAfter: java.time.LocalDateTime?,
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
