package com.ongo.infrastructure.external.wordpress

import com.ongo.infrastructure.external.wordpress.dto.*
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.DeleteExchange
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange
interface WordPressApi {

    @PostExchange("/rest/v1.1/sites/{siteId}/media/new")
    fun uploadMedia(
        @org.springframework.web.bind.annotation.PathVariable("siteId") siteId: String,
        @RequestParam("media_urls[]") mediaUrl: String,
        @RequestHeader("Authorization") authorization: String,
    ): WordPressMediaResponse

    @PostExchange("/rest/v1.1/sites/{siteId}/posts/new")
    fun createPost(
        @org.springframework.web.bind.annotation.PathVariable("siteId") siteId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: WordPressPostRequest,
    ): WordPressPostResponse

    @GetExchange("/rest/v1.1/sites/{siteId}/posts/{postId}")
    fun getPost(
        @org.springframework.web.bind.annotation.PathVariable("siteId") siteId: String,
        @org.springframework.web.bind.annotation.PathVariable("postId") postId: String,
        @RequestHeader("Authorization") authorization: String,
    ): WordPressPostResponse

    @PostExchange("/rest/v1.1/sites/{siteId}/posts/{postId}/delete")
    fun deletePost(
        @org.springframework.web.bind.annotation.PathVariable("siteId") siteId: String,
        @org.springframework.web.bind.annotation.PathVariable("postId") postId: String,
        @RequestHeader("Authorization") authorization: String,
    )

    @GetExchange("/rest/v1.1/me")
    fun getMe(
        @RequestHeader("Authorization") authorization: String,
    ): WordPressMeResponse

    @GetExchange("/rest/v1.1/sites/{siteId}/stats/post/{postId}")
    fun getPostStats(
        @org.springframework.web.bind.annotation.PathVariable("siteId") siteId: String,
        @org.springframework.web.bind.annotation.PathVariable("postId") postId: String,
        @RequestHeader("Authorization") authorization: String,
    ): WordPressPostStatsResponse

    // --- Comment API ---

    @GetExchange("/rest/v1.1/sites/{siteId}/posts/{postId}/replies")
    fun getComments(
        @org.springframework.web.bind.annotation.PathVariable("siteId") siteId: String,
        @org.springframework.web.bind.annotation.PathVariable("postId") postId: String,
        @RequestParam("number") number: Int,
        @RequestParam("page", required = false) page: Int?,
        @RequestHeader("Authorization") authorization: String,
    ): WordPressCommentsResponse

    @PostExchange("/rest/v1.1/sites/{siteId}/posts/{postId}/replies/new")
    fun createComment(
        @org.springframework.web.bind.annotation.PathVariable("siteId") siteId: String,
        @org.springframework.web.bind.annotation.PathVariable("postId") postId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody body: WordPressCommentRequest,
    ): WordPressCommentResponse

    @PostExchange("/rest/v1.1/sites/{siteId}/comments/{commentId}/delete")
    fun deleteComment(
        @org.springframework.web.bind.annotation.PathVariable("siteId") siteId: String,
        @org.springframework.web.bind.annotation.PathVariable("commentId") commentId: String,
        @RequestHeader("Authorization") authorization: String,
    ): WordPressDeleteResponse

    @PostExchange("/rest/v1.1/sites/{siteId}/comments/{commentId}/likes/new")
    fun likeComment(
        @org.springframework.web.bind.annotation.PathVariable("siteId") siteId: String,
        @org.springframework.web.bind.annotation.PathVariable("commentId") commentId: String,
        @RequestHeader("Authorization") authorization: String,
    ): WordPressLikeResponse
}

@HttpExchange
interface WordPressOAuthApi {

    @PostExchange("/oauth/token")
    fun exchangeToken(
        @RequestBody body: Map<String, String>,
    ): WordPressTokenResponse
}
