package com.ongo.infrastructure.external.youtube

import com.ongo.infrastructure.external.youtube.dto.YouTubeChannelListResponse
import com.ongo.infrastructure.external.youtube.dto.YouTubeTokenResponse
import com.ongo.infrastructure.external.youtube.dto.YouTubeUploadResponse
import com.ongo.infrastructure.external.youtube.dto.YouTubeVideoListResponse
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.DeleteExchange
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

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
}

@HttpExchange
interface GoogleOAuthApi {

    @PostExchange("/token")
    fun refreshToken(
        @RequestBody body: Map<String, String>,
    ): YouTubeTokenResponse
}
