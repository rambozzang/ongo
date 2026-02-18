package com.ongo.infrastructure.external.threads

import com.ongo.infrastructure.external.threads.dto.*
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange
interface ThreadsApi {

    @PostExchange("/{userId}/threads")
    fun createMediaContainer(
        @org.springframework.web.bind.annotation.PathVariable("userId") userId: String,
        @RequestParam("media_type") mediaType: String,
        @RequestParam("video_url") videoUrl: String,
        @RequestParam("text") text: String,
        @RequestParam("access_token") accessToken: String,
    ): ThreadsMediaContainerResponse

    @PostExchange("/{userId}/threads_publish")
    fun publishThread(
        @org.springframework.web.bind.annotation.PathVariable("userId") userId: String,
        @RequestParam("creation_id") creationId: String,
        @RequestParam("access_token") accessToken: String,
    ): ThreadsPublishResponse

    @GetExchange("/{threadId}")
    fun getThread(
        @org.springframework.web.bind.annotation.PathVariable("threadId") threadId: String,
        @RequestParam("fields") fields: String,
        @RequestParam("access_token") accessToken: String,
    ): ThreadsMediaResponse

    @GetExchange("/{threadId}/insights")
    fun getInsights(
        @org.springframework.web.bind.annotation.PathVariable("threadId") threadId: String,
        @RequestParam("metric") metric: String,
        @RequestParam("access_token") accessToken: String,
    ): ThreadsInsightsResponse

    @GetExchange("/me")
    fun getUser(
        @RequestParam("fields") fields: String,
        @RequestParam("access_token") accessToken: String,
    ): ThreadsUserResponse

    // --- Comment API ---

    @PostExchange("/{userId}/threads")
    fun createTextReplyContainer(
        @org.springframework.web.bind.annotation.PathVariable("userId") userId: String,
        @RequestParam("media_type") mediaType: String,
        @RequestParam("text") text: String,
        @RequestParam("reply_to_id") replyToId: String,
        @RequestParam("access_token") accessToken: String,
    ): ThreadsMediaContainerResponse

    @GetExchange("/{mediaId}/replies")
    fun getReplies(
        @org.springframework.web.bind.annotation.PathVariable("mediaId") mediaId: String,
        @RequestParam("fields") fields: String,
        @RequestParam("limit") limit: Int,
        @RequestParam("after", required = false) after: String?,
        @RequestParam("access_token") accessToken: String,
    ): ThreadsRepliesResponse

    @PostExchange("/{replyId}/manage_reply")
    fun hideReply(
        @org.springframework.web.bind.annotation.PathVariable("replyId") replyId: String,
        @RequestParam("hide") hide: Boolean,
        @RequestParam("access_token") accessToken: String,
    ): ThreadsHideReplyResponse
}

@HttpExchange
interface ThreadsOAuthApi {

    @PostExchange("/oauth/access_token")
    fun exchangeToken(
        @RequestParam("client_id") clientId: String,
        @RequestParam("client_secret") clientSecret: String,
        @RequestParam("code") code: String,
        @RequestParam("redirect_uri") redirectUri: String,
        @RequestParam("grant_type") grantType: String,
    ): ThreadsTokenResponse

    @GetExchange("/access_token")
    fun exchangeLongLivedToken(
        @RequestParam("grant_type") grantType: String,
        @RequestParam("client_secret") clientSecret: String,
        @RequestParam("access_token") accessToken: String,
    ): ThreadsTokenResponse
}
