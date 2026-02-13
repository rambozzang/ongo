package com.ongo.infrastructure.external.instagram

import com.ongo.infrastructure.external.instagram.dto.*
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange
interface InstagramApi {

    // Step 1: Create Reels media container
    @PostExchange("/{igUserId}/media")
    fun createMediaContainer(
        @PathVariable("igUserId") igUserId: String,
        @RequestParam("media_type") mediaType: String,
        @RequestParam("video_url") videoUrl: String,
        @RequestParam("caption") caption: String,
        @RequestParam("share_to_feed") shareToFeed: Boolean,
        @RequestParam("access_token") accessToken: String,
    ): InstagramContainerResponse

    // Step 2: Publish the media container
    @PostExchange("/{igUserId}/media_publish")
    fun publishMedia(
        @PathVariable("igUserId") igUserId: String,
        @RequestParam("creation_id") creationId: String,
        @RequestParam("access_token") accessToken: String,
    ): InstagramPublishResponse

    // Check container status (polling before publish)
    @GetExchange("/{containerId}")
    fun getContainerStatus(
        @PathVariable("containerId") containerId: String,
        @RequestParam("fields") fields: String,
        @RequestParam("access_token") accessToken: String,
    ): InstagramMediaStatusResponse

    // Get media details
    @GetExchange("/{mediaId}")
    fun getMedia(
        @PathVariable("mediaId") mediaId: String,
        @RequestParam("fields") fields: String,
        @RequestParam("access_token") accessToken: String,
    ): InstagramMediaResponse

    // Get media insights
    @GetExchange("/{mediaId}/insights")
    fun getMediaInsights(
        @PathVariable("mediaId") mediaId: String,
        @RequestParam("metric") metric: String,
        @RequestParam("access_token") accessToken: String,
    ): InstagramInsightsResponse

    // Get user profile
    @GetExchange("/me")
    fun getUserProfile(
        @RequestParam("fields") fields: String,
        @RequestParam("access_token") accessToken: String,
    ): InstagramUserResponse
}

@HttpExchange
interface InstagramOAuthApi {

    @PostExchange("/oauth/access_token")
    fun exchangeCodeForToken(
        @RequestBody body: Map<String, String>,
    ): InstagramTokenResponse

    @GetExchange("/refresh_access_token")
    fun refreshLongLivedToken(
        @RequestParam("grant_type") grantType: String,
        @RequestParam("access_token") accessToken: String,
    ): InstagramTokenResponse
}
