package com.ongo.infrastructure.external.tiktok

import com.ongo.infrastructure.external.tiktok.dto.*
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange
interface TikTokApi {

    @PostExchange("/v2/post/publish/video/init/")
    fun initVideoUpload(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: TikTokInitUploadRequest,
    ): TikTokInitUploadResponse

    @PostExchange("/v2/post/publish/status/fetch/")
    fun fetchPublishStatus(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: TikTokPublishStatusRequest,
    ): TikTokPublishStatusResponse

    @PostExchange("/v2/video/query/")
    fun queryVideos(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: TikTokVideoQueryRequest,
    ): TikTokVideoQueryResponse

    @GetExchange("/v2/user/info/")
    fun getCreatorInfo(
        @RequestHeader("Authorization") authorization: String,
    ): TikTokCreatorInfoResponse
}

@HttpExchange
interface TikTokOAuthApi {

    @PostExchange("/v2/oauth/token/")
    fun refreshToken(
        @RequestBody body: Map<String, String>,
    ): TikTokTokenResponse
}
