package com.ongo.infrastructure.external.dailymotion.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class DailymotionUploadUrlResponse(
    @JsonProperty("upload_url") val uploadUrl: String,
    @JsonProperty("progress_url") val progressUrl: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DailymotionUploadResult(
    val url: String, // uploaded video URL to pass to publish
)

data class DailymotionPublishRequest(
    val url: String,
    val title: String,
    val description: String? = null,
    val tags: String? = null, // comma-separated
    val channel: String = "creation",
    val published: Boolean = true,
    @JsonProperty("private") val isPrivate: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DailymotionVideoResponse(
    val id: String,
    val title: String?,
    val url: String?,
    val status: String?, // published, processing, deleted, etc
    @JsonProperty("views_total") val viewsTotal: Long?,
    @JsonProperty("likes_total") val likesTotal: Long?,
    @JsonProperty("comments_total") val commentsTotal: Long?,
    @JsonProperty("bookmarks_total") val bookmarksTotal: Long?,
    val duration: Int?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DailymotionUserResponse(
    val id: String,
    val screenname: String?,
    val url: String?,
    @JsonProperty("avatar_720_url") val avatarUrl: String?,
    @JsonProperty("followers_total") val followersTotal: Long?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DailymotionTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("refresh_token") val refreshToken: String?,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("token_type") val tokenType: String?,
)

// --- Comment API ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class DailymotionCommentsResponse(
    val list: List<DailymotionCommentData>? = null,
    val total: Int? = null,
    val page: Int? = null,
    @JsonProperty("has_more") val hasMore: Boolean? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DailymotionCommentData(
        val id: String? = null,
        val message: String? = null,
        @JsonProperty("owner.screenname") val ownerScreenname: String? = null,
        @JsonProperty("owner.avatar_60_url") val ownerAvatarUrl: String? = null,
        @JsonProperty("owner.url") val ownerUrl: String? = null,
        @JsonProperty("created_time") val createdTime: Long? = null,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class DailymotionCommentResponse(
    val id: String? = null,
)
