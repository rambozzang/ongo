package com.ongo.infrastructure.external.twitter.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// --- Tweet ---
data class TwitterCreateTweetRequest(
    val text: String,
    val media: MediaPayload? = null,
    val reply: ReplyPayload? = null,
) {
    data class MediaPayload(
        @JsonProperty("media_ids") val mediaIds: List<String>,
    )

    data class ReplyPayload(
        @JsonProperty("in_reply_to_tweet_id") val inReplyToTweetId: String,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TwitterCreateTweetResponse(
    val data: TweetData?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TweetData(
        val id: String,
        val text: String?,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TwitterTweetResponse(
    val data: TweetData?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TweetData(
        val id: String,
        val text: String?,
        @JsonProperty("public_metrics") val publicMetrics: PublicMetrics?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PublicMetrics(
        @JsonProperty("impression_count") val impressionCount: Long?,
        @JsonProperty("like_count") val likeCount: Long?,
        @JsonProperty("reply_count") val replyCount: Long?,
        @JsonProperty("retweet_count") val retweetCount: Long?,
        @JsonProperty("bookmark_count") val bookmarkCount: Long?,
    )
}

// --- Media Upload (v1.1) ---
data class TwitterMediaInitRequest(
    val command: String = "INIT",
    @JsonProperty("total_bytes") val totalBytes: Long,
    @JsonProperty("media_type") val mediaType: String = "video/mp4",
    @JsonProperty("media_category") val mediaCategory: String = "tweet_video",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TwitterMediaInitResponse(
    @JsonProperty("media_id_string") val mediaIdString: String,
    @JsonProperty("expires_after_secs") val expiresAfterSecs: Long?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TwitterMediaStatusResponse(
    @JsonProperty("media_id_string") val mediaIdString: String,
    @JsonProperty("processing_info") val processingInfo: ProcessingInfo?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ProcessingInfo(
        val state: String?, // pending, in_progress, failed, succeeded
        @JsonProperty("check_after_secs") val checkAfterSecs: Int?,
        val error: ErrorInfo?,
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class ErrorInfo(
            val code: Int?,
            val message: String?,
        )
    }
}

// --- User ---
@JsonIgnoreProperties(ignoreUnknown = true)
data class TwitterUserResponse(
    val data: UserData?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UserData(
        val id: String,
        val name: String?,
        val username: String?,
        @JsonProperty("profile_image_url") val profileImageUrl: String?,
        @JsonProperty("public_metrics") val publicMetrics: UserMetrics?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UserMetrics(
        @JsonProperty("followers_count") val followersCount: Long?,
    )
}

// --- Comment / Search ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class TwitterSearchResponse(
    val data: List<TwitterTweetData>? = null,
    val includes: TwitterIncludes? = null,
    val meta: TwitterSearchMeta? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TwitterTweetData(
        val id: String,
        val text: String?,
        @JsonProperty("author_id") val authorId: String?,
        @JsonProperty("in_reply_to_user_id") val inReplyToUserId: String?,
        @JsonProperty("public_metrics") val publicMetrics: PublicMetrics? = null,
        @JsonProperty("created_at") val createdAt: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PublicMetrics(
        @JsonProperty("like_count") val likeCount: Int? = null,
        @JsonProperty("reply_count") val replyCount: Int? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TwitterIncludes(
        val users: List<TwitterIncludeUser>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TwitterIncludeUser(
        val id: String,
        val name: String?,
        val username: String?,
        @JsonProperty("profile_image_url") val profileImageUrl: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TwitterSearchMeta(
        @JsonProperty("next_token") val nextToken: String? = null,
        @JsonProperty("result_count") val resultCount: Int? = null,
    )
}

data class TwitterLikeRequest(
    @JsonProperty("tweet_id") val tweetId: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TwitterLikeResponse(
    val data: LikeData? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LikeData(
        val liked: Boolean?,
    )
}

// --- OAuth ---
@JsonIgnoreProperties(ignoreUnknown = true)
data class TwitterTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("refresh_token") val refreshToken: String?,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("token_type") val tokenType: String?,
)
