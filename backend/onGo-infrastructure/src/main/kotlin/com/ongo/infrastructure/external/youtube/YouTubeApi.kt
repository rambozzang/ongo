package com.ongo.infrastructure.external.youtube

import com.ongo.infrastructure.external.youtube.dto.*
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.DeleteExchange
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange
import org.springframework.web.service.annotation.PutExchange

@HttpExchange
interface YouTubeApi {

    @PostExchange("/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
    fun initiateResumableUpload(
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("Content-Type") contentType: String,
        @RequestBody metadata: Any,
    ): YouTubeUploadResponse

    @GetExchange("/youtube/v3/videos")
    fun listVideos(
        @RequestParam("id") id: String,
        @RequestParam("part") part: String,
        @RequestHeader("Authorization") authorization: String,
    ): YouTubeVideoListResponse

    @GetExchange("/youtube/v3/channels")
    fun listChannels(
        @RequestParam("part") part: String,
        @RequestParam("mine") mine: Boolean,
        @RequestHeader("Authorization") authorization: String,
    ): YouTubeChannelListResponse

    @PutExchange("/youtube/v3/videos?part=snippet")
    fun updateVideo(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody body: Any,
    ): YouTubeVideoListResponse

    @DeleteExchange("/youtube/v3/videos")
    fun deleteVideo(
        @RequestParam("id") id: String,
        @RequestHeader("Authorization") authorization: String,
    )

    @GetExchange("/youtube/v3/channels")
    fun searchChannelByHandle(
        @RequestParam("part") part: String,
        @RequestParam("forHandle") forHandle: String,
        @RequestParam("key") key: String,
    ): YouTubeChannelListResponse

    @GetExchange("/youtube/v3/channels")
    fun searchChannelById(
        @RequestParam("part") part: String,
        @RequestParam("id") id: String,
        @RequestParam("key") key: String,
    ): YouTubeChannelListResponse

    @GetExchange("/youtube/v3/playlistItems")
    fun listPlaylistItems(
        @RequestParam("playlistId") playlistId: String,
        @RequestParam("part") part: String,
        @RequestParam("maxResults") maxResults: Int,
        @RequestParam("pageToken", required = false) pageToken: String?,
        @RequestHeader("Authorization") authorization: String,
    ): YouTubePlaylistItemListResponse

    // --- Comment API ---

    @GetExchange("/youtube/v3/commentThreads")
    fun listCommentThreads(
        @RequestParam("videoId") videoId: String,
        @RequestParam("part") part: String,
        @RequestParam("maxResults") maxResults: Int,
        @RequestParam("pageToken", required = false) pageToken: String?,
        @RequestParam("order") order: String,
        @RequestHeader("Authorization") authorization: String,
    ): YouTubeCommentThreadListResponse

    @PostExchange("/youtube/v3/comments?part=snippet")
    fun insertComment(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody body: YouTubeCommentInsertRequest,
    ): YouTubeCommentResponse

    @DeleteExchange("/youtube/v3/comments")
    fun deleteComment(
        @RequestParam("id") id: String,
        @RequestHeader("Authorization") authorization: String,
    )

    @PostExchange("/youtube/v3/comments/setModerationStatus")
    fun setModerationStatus(
        @RequestParam("id") id: String,
        @RequestParam("moderationStatus") moderationStatus: String,
        @RequestHeader("Authorization") authorization: String,
    )
}

@HttpExchange
interface YouTubeAnalyticsApi {

    @GetExchange("/v2/reports")
    fun queryAnalytics(
        @RequestParam("ids") ids: String,
        @RequestParam("startDate") startDate: String,
        @RequestParam("endDate") endDate: String,
        @RequestParam("metrics") metrics: String,
        @RequestParam("filters") filters: String,
        @RequestHeader("Authorization") authorization: String,
    ): com.ongo.infrastructure.external.youtube.dto.YouTubeAnalyticsResponse

    /**
     * 금전 지표 전용 질의.
     *
     * **[queryAnalytics] 와 절대 합치지 않는다.** `estimatedRevenue` 는
     * `yt-analytics-monetary.readonly` 를 따로 요구하고, 그 권한이 없으면 응답 전체가
     * 403 이다. 한 질의에 섞으면 수익 권한 하나 때문에 조회수·좋아요·댓글까지 전부
     * 저장되지 않는다.
     *
     * [currency] 는 ISO 4217. 지정하지 않으면 채널의 지급 통화로 내려와 원화로 읽을 수
     * 없다. [dimensions] 로 일별 행을 받아야 하루 단위로 저장할 수 있다.
     */
    @GetExchange("/v2/reports")
    fun queryRevenue(
        @RequestParam("ids") ids: String,
        @RequestParam("startDate") startDate: String,
        @RequestParam("endDate") endDate: String,
        @RequestParam("metrics") metrics: String,
        @RequestParam("dimensions") dimensions: String,
        @RequestParam("filters") filters: String,
        @RequestParam("currency") currency: String,
        @RequestHeader("Authorization") authorization: String,
    ): com.ongo.infrastructure.external.youtube.dto.YouTubeAnalyticsResponse
}

@HttpExchange
interface GoogleOAuthApi {

    @PostExchange("/token")
    fun refreshToken(
        @RequestBody body: Map<String, String>,
    ): YouTubeTokenResponse

    @PostExchange("/revoke")
    fun revokeToken(
        @RequestParam("token") token: String,
    )
}
