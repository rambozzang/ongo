package com.ongo.infrastructure.external.naverclip

import com.ongo.infrastructure.external.naverclip.dto.*
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.DeleteExchange
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange
interface NaverClipApi {

    @PostExchange("/v1/clips/upload/init")
    fun initUpload(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: NaverClipUploadInitRequest,
    ): NaverClipUploadInitResponse

    @PostExchange("/v1/clips/upload/complete")
    fun completeUpload(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: NaverClipUploadCompleteRequest,
    ): NaverClipUploadCompleteResponse

    @GetExchange("/v1/clips/{clipId}/status")
    fun getClipStatus(
        @PathVariable("clipId") clipId: String,
        @RequestHeader("Authorization") authorization: String,
    ): NaverClipStatusResponse

    @GetExchange("/v1/clips/{clipId}/statistics")
    fun getClipStatistics(
        @PathVariable("clipId") clipId: String,
        @RequestParam("start_date") startDate: String,
        @RequestParam("end_date") endDate: String,
        @RequestHeader("Authorization") authorization: String,
    ): NaverClipStatisticsResponse

    @GetExchange("/v1/channels/me")
    fun getChannelInfo(
        @RequestHeader("Authorization") authorization: String,
    ): NaverClipChannelResponse

    @DeleteExchange("/v1/clips/{clipId}")
    fun deleteClip(
        @PathVariable("clipId") clipId: String,
        @RequestHeader("Authorization") authorization: String,
    )
}

@HttpExchange
interface NaverOAuthApi {

    @PostExchange("/oauth2.0/token")
    fun refreshToken(
        @RequestBody body: Map<String, String>,
    ): NaverClipTokenResponse
}
