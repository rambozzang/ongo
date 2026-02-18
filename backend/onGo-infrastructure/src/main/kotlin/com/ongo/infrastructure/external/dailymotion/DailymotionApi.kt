package com.ongo.infrastructure.external.dailymotion

import com.ongo.infrastructure.external.dailymotion.dto.*
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.DeleteExchange
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange
interface DailymotionApi {

    @GetExchange("/file/upload")
    fun getUploadUrl(
        @RequestHeader("Authorization") authorization: String,
    ): DailymotionUploadUrlResponse

    @PostExchange("/me/videos")
    fun publishVideo(
        @RequestHeader("Authorization") authorization: String,
        @RequestParam("url") url: String,
        @RequestParam("title") title: String,
        @RequestParam("description") description: String,
        @RequestParam("tags") tags: String,
        @RequestParam("published") published: Boolean,
        @RequestParam("is_created_for_kids") isCreatedForKids: Boolean,
    ): DailymotionVideoResponse

    @GetExchange("/video/{videoId}")
    fun getVideo(
        @org.springframework.web.bind.annotation.PathVariable("videoId") videoId: String,
        @RequestParam("fields") fields: String,
        @RequestHeader("Authorization") authorization: String,
    ): DailymotionVideoResponse

    @DeleteExchange("/video/{videoId}")
    fun deleteVideo(
        @org.springframework.web.bind.annotation.PathVariable("videoId") videoId: String,
        @RequestHeader("Authorization") authorization: String,
    )

    @GetExchange("/me")
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

    @PostExchange("/oauth/token")
    fun exchangeToken(
        @RequestBody body: Map<String, String>,
    ): DailymotionTokenResponse
}
