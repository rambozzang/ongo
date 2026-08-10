package com.ongo.infrastructure.external.linkedin

import com.ongo.infrastructure.external.linkedin.dto.*
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.DeleteExchange
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange
interface LinkedInApi {

    @PostExchange("/v2/assets?action=registerUpload")
    fun registerUpload(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: LinkedInRegisterUploadRequest,
    ): LinkedInRegisterUploadResponse

    @PostExchange("/v2/ugcPosts")
    fun createUgcPost(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: LinkedInUgcPostRequest,
    ): LinkedInUgcPostResponse

    @GetExchange("/v2/ugcPosts/{postId}")
    fun getUgcPost(
        @org.springframework.web.bind.annotation.PathVariable("postId") postId: String,
        @RequestHeader("Authorization") authorization: String,
    ): LinkedInUgcPostResponse

    @GetExchange("/v2/me")
    fun getProfile(
        @RequestParam("projection", required = false) projection: String? = null,
        @RequestHeader("Authorization") authorization: String,
    ): LinkedInProfileResponse

    @DeleteExchange("/v2/ugcPosts/{postId}")
    fun deletePost(
        @org.springframework.web.bind.annotation.PathVariable("postId") postId: String,
        @RequestHeader("Authorization") authorization: String,
    )

    @GetExchange("/v2/organizationalEntityShareStatistics")
    fun getShareStatistics(
        @RequestParam("q") q: String,
        @RequestParam("shares") shares: String,
        @RequestHeader("Authorization") authorization: String,
    ): LinkedInShareStatsResponse

    // --- Comment API ---

    @GetExchange("/v2/socialActions/{activityId}/comments")
    fun getComments(
        @org.springframework.web.bind.annotation.PathVariable("activityId") activityId: String,
        @RequestParam("start") start: Int,
        @RequestParam("count") count: Int,
        @RequestHeader("Authorization") authorization: String,
    ): LinkedInCommentsResponse

    @PostExchange("/v2/socialActions/{activityId}/comments")
    fun createComment(
        @org.springframework.web.bind.annotation.PathVariable("activityId") activityId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody body: LinkedInCommentRequest,
    ): LinkedInCommentResponse

    @PostExchange("/v2/socialActions/{activityId}/likes")
    fun likeActivity(
        @org.springframework.web.bind.annotation.PathVariable("activityId") activityId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody body: LinkedInLikeRequest,
    )

    @DeleteExchange("/v2/socialActions/{activityId}/comments/{commentId}")
    fun deleteComment(
        @org.springframework.web.bind.annotation.PathVariable("activityId") activityId: String,
        @org.springframework.web.bind.annotation.PathVariable("commentId") commentId: String,
        @RequestHeader("Authorization") authorization: String,
    )
}

@HttpExchange
interface LinkedInOAuthApi {

    @PostExchange("/oauth/v2/accessToken", contentType = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    fun exchangeToken(
        @RequestHeader("Content-Type") contentType: String,
        @RequestBody body: org.springframework.util.MultiValueMap<String, String>,
    ): LinkedInTokenResponse
}

@HttpExchange
interface LinkedInVideosApi {

    @PostExchange("/rest/videos?action=initializeUpload")
    fun initializeUpload(
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("Linkedin-Version") linkedinVersion: String,
        @RequestHeader("X-Restli-Protocol-Version") restliVersion: String,
        @RequestBody request: LinkedInVideoInitializeRequest,
    ): LinkedInVideoInitializeResponse

    @PostExchange("/rest/videos?action=finalizeUpload")
    fun finalizeUpload(
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("Linkedin-Version") linkedinVersion: String,
        @RequestHeader("X-Restli-Protocol-Version") restliVersion: String,
        @RequestBody request: LinkedInVideoFinalizeRequest,
    )

    @GetExchange("/rest/videos/{videoUrn}")
    fun getVideo(
        @org.springframework.web.bind.annotation.PathVariable("videoUrn") videoUrn: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("Linkedin-Version") linkedinVersion: String,
        @RequestHeader("X-Restli-Protocol-Version") restliVersion: String,
    ): LinkedInVideoStatusResponse
}
