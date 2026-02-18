package com.ongo.infrastructure.external.tumblr

import com.ongo.infrastructure.external.tumblr.dto.*
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.service.annotation.DeleteExchange
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange
interface TumblrApi {

    @PostExchange("/v2/blog/{blogName}/posts")
    fun createPost(
        @org.springframework.web.bind.annotation.PathVariable("blogName") blogName: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: TumblrNpfPostRequest,
    ): TumblrPostResponse

    @GetExchange("/v2/blog/{blogName}/posts/{postId}")
    fun getPost(
        @org.springframework.web.bind.annotation.PathVariable("blogName") blogName: String,
        @org.springframework.web.bind.annotation.PathVariable("postId") postId: String,
        @RequestHeader("Authorization") authorization: String,
    ): TumblrGetPostResponse

    @DeleteExchange("/v2/blog/{blogName}/post/delete")
    fun deletePost(
        @org.springframework.web.bind.annotation.PathVariable("blogName") blogName: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody body: Map<String, String>,
    )

    @GetExchange("/v2/blog/{blogName}/posts/{postId}/notes")
    fun getPostNotes(
        @org.springframework.web.bind.annotation.PathVariable("blogName") blogName: String,
        @org.springframework.web.bind.annotation.PathVariable("postId") postId: String,
        @RequestHeader("Authorization") authorization: String,
    ): TumblrNotesResponse

    @GetExchange("/v2/user/info")
    fun getUserInfo(
        @RequestHeader("Authorization") authorization: String,
    ): TumblrUserInfoResponse
}

@HttpExchange
interface TumblrOAuthApi {

    @PostExchange("/v2/oauth2/token")
    fun exchangeToken(
        @RequestBody body: Map<String, String>,
    ): TumblrTokenResponse
}
