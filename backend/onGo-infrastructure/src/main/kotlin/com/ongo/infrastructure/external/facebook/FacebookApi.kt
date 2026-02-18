package com.ongo.infrastructure.external.facebook

import com.ongo.infrastructure.external.facebook.dto.*
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.DeleteExchange
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange
interface FacebookApi {

    @PostExchange("/{pageId}/videos")
    fun uploadVideo(
        @org.springframework.web.bind.annotation.PathVariable("pageId") pageId: String,
        @RequestParam("file_url") fileUrl: String,
        @RequestParam("title") title: String,
        @RequestParam("description") description: String,
        @RequestParam("access_token") accessToken: String,
    ): FacebookVideoUploadResponse

    @GetExchange("/{videoId}")
    fun getVideo(
        @org.springframework.web.bind.annotation.PathVariable("videoId") videoId: String,
        @RequestParam("fields") fields: String,
        @RequestParam("access_token") accessToken: String,
    ): FacebookVideoResponse

    @DeleteExchange("/{videoId}")
    fun deleteVideo(
        @org.springframework.web.bind.annotation.PathVariable("videoId") videoId: String,
        @RequestParam("access_token") accessToken: String,
    )

    @GetExchange("/me/accounts")
    fun getPages(
        @RequestParam("fields") fields: String,
        @RequestParam("access_token") accessToken: String,
    ): FacebookPagesResponse

    @GetExchange("/{videoId}/video_insights")
    fun getVideoInsights(
        @org.springframework.web.bind.annotation.PathVariable("videoId") videoId: String,
        @RequestParam("metric") metric: String,
        @RequestParam("access_token") accessToken: String,
    ): FacebookInsightsResponse

    // --- Comment API ---

    @GetExchange("/{postId}/comments")
    fun getComments(
        @org.springframework.web.bind.annotation.PathVariable("postId") postId: String,
        @RequestParam("fields") fields: String,
        @RequestParam("limit") limit: Int,
        @RequestParam("after", required = false) after: String?,
        @RequestParam("access_token") accessToken: String,
    ): FacebookCommentsResponse

    @PostExchange("/{commentId}/comments")
    fun replyToComment(
        @org.springframework.web.bind.annotation.PathVariable("commentId") commentId: String,
        @RequestParam("message") message: String,
        @RequestParam("access_token") accessToken: String,
    ): FacebookCommentReplyResponse

    @PostExchange("/{commentId}/likes")
    fun likeComment(
        @org.springframework.web.bind.annotation.PathVariable("commentId") commentId: String,
        @RequestParam("access_token") accessToken: String,
    ): FacebookLikeResponse

    @DeleteExchange("/{commentId}")
    fun deleteComment(
        @org.springframework.web.bind.annotation.PathVariable("commentId") commentId: String,
        @RequestParam("access_token") accessToken: String,
    )
}

@HttpExchange
interface FacebookOAuthApi {

    @GetExchange("/oauth/access_token")
    fun exchangeToken(
        @RequestParam("client_id") clientId: String,
        @RequestParam("client_secret") clientSecret: String,
        @RequestParam("code") code: String,
        @RequestParam("redirect_uri") redirectUri: String,
    ): FacebookTokenResponse

    @GetExchange("/oauth/access_token")
    fun refreshToken(
        @RequestParam("grant_type") grantType: String,
        @RequestParam("client_id") clientId: String,
        @RequestParam("client_secret") clientSecret: String,
        @RequestParam("fb_exchange_token") fbExchangeToken: String,
    ): FacebookTokenResponse
}
