package com.ongo.infrastructure.external.twitter

import com.ongo.infrastructure.external.twitter.dto.*
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.DeleteExchange
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange
interface TwitterApi {

    @PostExchange("/2/tweets")
    fun createTweet(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: TwitterCreateTweetRequest,
    ): TwitterCreateTweetResponse

    @GetExchange("/2/tweets/{tweetId}")
    fun getTweet(
        @org.springframework.web.bind.annotation.PathVariable("tweetId") tweetId: String,
        @RequestParam("tweet.fields") tweetFields: String,
        @RequestHeader("Authorization") authorization: String,
    ): TwitterTweetResponse

    @DeleteExchange("/2/tweets/{tweetId}")
    fun deleteTweet(
        @org.springframework.web.bind.annotation.PathVariable("tweetId") tweetId: String,
        @RequestHeader("Authorization") authorization: String,
    )

    @GetExchange("/2/users/me")
    fun getMe(
        @RequestParam("user.fields") userFields: String,
        @RequestHeader("Authorization") authorization: String,
    ): TwitterUserResponse

    // --- Comment API (Replies) ---

    @GetExchange("/2/tweets/search/recent")
    fun searchReplies(
        @RequestParam("query") query: String,
        @RequestParam("tweet.fields") tweetFields: String,
        @RequestParam("expansions") expansions: String,
        @RequestParam("user.fields") userFields: String,
        @RequestParam("max_results") maxResults: Int,
        @RequestParam("next_token", required = false) nextToken: String?,
        @RequestHeader("Authorization") authorization: String,
    ): TwitterSearchResponse

    @PostExchange("/2/users/{userId}/likes")
    fun likeTweet(
        @org.springframework.web.bind.annotation.PathVariable("userId") userId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody body: TwitterLikeRequest,
    ): TwitterLikeResponse
}

@HttpExchange
interface TwitterMediaApi {

    @PostExchange("/1.1/media/upload.json")
    fun initUpload(
        @RequestHeader("Authorization") authorization: String,
        @RequestParam("command") command: String,
        @RequestParam("total_bytes") totalBytes: Long,
        @RequestParam("media_type") mediaType: String,
        @RequestParam("media_category") mediaCategory: String,
    ): TwitterMediaInitResponse

    @PostExchange("/1.1/media/upload.json")
    fun finalizeUpload(
        @RequestHeader("Authorization") authorization: String,
        @RequestParam("command") command: String,
        @RequestParam("media_id") mediaId: String,
    ): TwitterMediaStatusResponse

    @GetExchange("/1.1/media/upload.json")
    fun checkStatus(
        @RequestHeader("Authorization") authorization: String,
        @RequestParam("command") command: String,
        @RequestParam("media_id") mediaId: String,
    ): TwitterMediaStatusResponse
}

@HttpExchange
interface TwitterOAuthApi {

    @PostExchange("/2/oauth2/token")
    fun exchangeToken(
        @RequestBody body: Map<String, String>,
    ): TwitterTokenResponse
}
