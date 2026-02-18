package com.ongo.infrastructure.external.threads.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

data class ThreadsMediaContainerRequest(
    @JsonProperty("media_type") val mediaType: String = "VIDEO",
    @JsonProperty("video_url") val videoUrl: String,
    val text: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ThreadsMediaContainerResponse(
    val id: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ThreadsPublishResponse(
    val id: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ThreadsMediaResponse(
    val id: String,
    val text: String?,
    val timestamp: String?,
    @JsonProperty("media_type") val mediaType: String?,
    @JsonProperty("media_url") val mediaUrl: String?,
    val permalink: String?,
    @JsonProperty("is_quote_post") val isQuotePost: Boolean?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ThreadsInsightsResponse(
    val data: List<InsightEntry> = emptyList(),
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class InsightEntry(
        val name: String?,
        val values: List<InsightValue>?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class InsightValue(
        val value: Long?,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ThreadsUserResponse(
    val id: String,
    val username: String?,
    val name: String?,
    @JsonProperty("threads_profile_picture_url") val profilePictureUrl: String?,
    @JsonProperty("threads_biography") val biography: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ThreadsTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("token_type") val tokenType: String?,
    @JsonProperty("expires_in") val expiresIn: Long?,
)

// --- Comment DTOs ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class ThreadsRepliesResponse(
    val data: List<ThreadsReplyData>? = null,
    val paging: ThreadsPaging? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ThreadsReplyData(
        val id: String?,
        val text: String? = null,
        val username: String? = null,
        val timestamp: String? = null,
        @JsonProperty("like_count") val likeCount: Int? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ThreadsPaging(
        val cursors: Cursors? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Cursors(
        val after: String? = null,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ThreadsHideReplyResponse(
    val success: Boolean? = null,
)
