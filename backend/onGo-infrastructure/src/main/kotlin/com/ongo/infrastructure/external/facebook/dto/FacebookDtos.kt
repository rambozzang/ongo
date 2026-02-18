package com.ongo.infrastructure.external.facebook.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class FacebookVideoUploadResponse(
    val id: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FacebookVideoResponse(
    val id: String,
    val title: String?,
    val description: String?,
    val status: VideoStatus?,
    @JsonProperty("permalink_url") val permalinkUrl: String?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class VideoStatus(
        @JsonProperty("video_status") val videoStatus: String?,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class FacebookPagesResponse(
    val data: List<PageInfo> = emptyList(),
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PageInfo(
        val id: String,
        val name: String?,
        @JsonProperty("access_token") val accessToken: String?,
        val category: String?,
        @JsonProperty("followers_count") val followersCount: Long?,
        val picture: Picture?,
        val link: String?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Picture(
        val data: PictureData?,
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class PictureData(
            val url: String?,
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class FacebookInsightsResponse(
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
data class FacebookTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("token_type") val tokenType: String?,
    @JsonProperty("expires_in") val expiresIn: Long?,
)

// --- Comment DTOs ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class FacebookCommentsResponse(
    val data: List<FacebookCommentData>? = null,
    val paging: FacebookPaging? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class FacebookCommentData(
        val id: String?,
        val message: String?,
        val from: FacebookFrom? = null,
        @JsonProperty("like_count") val likeCount: Int? = null,
        @JsonProperty("comment_count") val commentCount: Int? = null,
        @JsonProperty("created_time") val createdTime: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class FacebookFrom(
        val id: String? = null,
        val name: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class FacebookPaging(
        val cursors: Cursors? = null,
        val next: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Cursors(
        val after: String? = null,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class FacebookCommentReplyResponse(
    val id: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FacebookLikeResponse(
    val success: Boolean? = null,
)
