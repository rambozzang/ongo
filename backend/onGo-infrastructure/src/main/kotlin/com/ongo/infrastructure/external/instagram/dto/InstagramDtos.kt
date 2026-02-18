package com.ongo.infrastructure.external.instagram.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// --- Media Container (Step 1: Create container) ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstagramContainerResponse(
    val id: String?,
    val error: InstagramError?,
)

// --- Publish (Step 2: Publish container) ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstagramPublishResponse(
    val id: String?, // Media ID
    val error: InstagramError?,
)

// --- Media Status ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstagramMediaStatusResponse(
    val id: String?,
    @JsonProperty("status_code") val statusCode: String?, // EXPIRED, ERROR, FINISHED, IN_PROGRESS, PUBLISHED
    val error: InstagramError?,
)

// --- Media Insights ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstagramInsightsResponse(
    val data: List<InsightMetric>?,
    val error: InstagramError?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class InsightMetric(
        val name: String?,
        val period: String?,
        val values: List<InsightValue>?,
        val title: String?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class InsightValue(
        val value: Long?,
    )
}

// --- Media Detail ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstagramMediaResponse(
    val id: String?,
    val caption: String?,
    @JsonProperty("media_type") val mediaType: String?,
    @JsonProperty("media_url") val mediaUrl: String?,
    val permalink: String?,
    val timestamp: String?,
    @JsonProperty("like_count") val likeCount: Long?,
    @JsonProperty("comments_count") val commentsCount: Long?,
    val error: InstagramError?,
)

// --- User Profile ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstagramUserResponse(
    val id: String?,
    val username: String?,
    val name: String?,
    @JsonProperty("profile_picture_url") val profilePictureUrl: String?,
    @JsonProperty("followers_count") val followersCount: Long?,
    @JsonProperty("media_count") val mediaCount: Long?,
    val error: InstagramError?,
)

// --- OAuth Token ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstagramTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("token_type") val tokenType: String?,
    @JsonProperty("expires_in") val expiresIn: Long?,
)

// --- Comments ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstagramCommentsResponse(
    val data: List<InstagramCommentData>? = null,
    val paging: Paging? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class InstagramCommentData(
        val id: String?,
        val text: String?,
        val username: String?,
        @JsonProperty("like_count") val likeCount: Int?,
        val timestamp: String?,
        val replies: RepliesData? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class RepliesData(
        val data: List<InstagramCommentData>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Paging(
        val cursors: Cursors? = null,
        val next: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Cursors(
        val after: String? = null,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstagramCommentReplyResponse(
    val id: String? = null,
)

// --- Common Error ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstagramError(
    val message: String?,
    val type: String?,
    val code: Int?,
    @JsonProperty("error_subcode") val errorSubcode: Int?,
    @JsonProperty("fbtrace_id") val fbtraceId: String?,
)
