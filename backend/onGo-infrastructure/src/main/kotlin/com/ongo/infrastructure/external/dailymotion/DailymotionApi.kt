package com.ongo.infrastructure.external.dailymotion

import com.ongo.infrastructure.external.dailymotion.dto.*
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.util.MultiValueMap
import org.springframework.http.MediaType
import org.springframework.web.service.annotation.DeleteExchange
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PatchExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange
interface DailymotionApi {

    @PostExchange("/v2/files/upload_sessions")
    fun createUploadSession(
        @RequestHeader("Authorization") authorization: String,
    ): DailymotionUploadUrlResponse

    @PostExchange("/v2/profiles/{profileId}/videos")
    fun createVideo(
        @org.springframework.web.bind.annotation.PathVariable("profileId") profileId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: DailymotionCreateVideoRequest,
    ): DailymotionVideoResponse

    @GetExchange("/v2/videos/{videoId}")
    fun getVideoV2(
        @org.springframework.web.bind.annotation.PathVariable("videoId") videoId: String,
        @RequestParam("fields") fields: String,
        @RequestHeader("Authorization") authorization: String,
    ): DailymotionVideoResponse

    /** Legacy video fields still expose aggregate engagement counters. */
    @GetExchange("/video/{videoId}")
    fun getVideoLegacy(
        @org.springframework.web.bind.annotation.PathVariable("videoId") videoId: String,
        @RequestParam("fields") fields: String,
        @RequestHeader("Authorization") authorization: String,
    ): DailymotionVideoResponse

    @DeleteExchange("/v2/videos/{videoId}")
    fun deleteVideo(
        @org.springframework.web.bind.annotation.PathVariable("videoId") videoId: String,
        @RequestHeader("Authorization") authorization: String,
    )

    @PatchExchange("/v2/videos/{videoId}")
    fun updateVideo(
        @org.springframework.web.bind.annotation.PathVariable("videoId") videoId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: DailymotionUpdateVideoRequest,
    )

    @GetExchange("/v2/me")
    fun getUser(
        @RequestParam("fields") fields: String,
        @RequestHeader("Authorization") authorization: String,
    ): DailymotionUserResponse

    // --- Comment API ---

    @GetExchange("/video/{videoId}/comments")
    fun getComments(
        @org.springframework.web.bind.annotation.PathVariable("videoId") videoId: String,
        @RequestParam("fields") fields: String,
        @RequestParam("limit") limit: Int,
        @RequestParam("page", required = false) page: Int?,
        @RequestHeader("Authorization") authorization: String,
    ): DailymotionCommentsResponse

    @PostExchange("/video/{videoId}/comments")
    fun createComment(
        @org.springframework.web.bind.annotation.PathVariable("videoId") videoId: String,
        @RequestParam("message") message: String,
        @RequestHeader("Authorization") authorization: String,
    ): DailymotionCommentResponse

    @DeleteExchange("/comment/{commentId}")
    fun deleteComment(
        @org.springframework.web.bind.annotation.PathVariable("commentId") commentId: String,
        @RequestHeader("Authorization") authorization: String,
    )
}

@HttpExchange
interface DailymotionOAuthApi {

    @PostExchange(value = "/oauth/token", contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    fun exchangeToken(
        @RequestBody body: MultiValueMap<String, String>,
    ): DailymotionTokenResponse
}
