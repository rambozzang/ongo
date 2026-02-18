package com.ongo.infrastructure.external.pinterest.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

data class PinterestMediaRequest(
    @JsonProperty("media_type") val mediaType: String = "video",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PinterestMediaResponse(
    @JsonProperty("media_id") val mediaId: String,
    val status: String?,
    @JsonProperty("upload_url") val uploadUrl: String?,
    @JsonProperty("upload_parameters") val uploadParameters: Map<String, String>?,
)

data class PinterestPinRequest(
    val title: String,
    val description: String,
    val link: String? = null,
    @JsonProperty("board_id") val boardId: String? = null,
    @JsonProperty("media_source") val mediaSource: MediaSource,
) {
    data class MediaSource(
        @JsonProperty("source_type") val sourceType: String = "video_id",
        @JsonProperty("media_id") val mediaId: String,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PinterestPinResponse(
    val id: String,
    val title: String?,
    val description: String?,
    val link: String?,
    val media: MediaInfo?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MediaInfo(
        @JsonProperty("media_type") val mediaType: String?,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PinterestPinAnalyticsResponse(
    val all: AnalyticsData?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AnalyticsData(
        val lifetime_metrics: Map<String, Long>?,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PinterestUserResponse(
    val username: String?,
    @JsonProperty("account_type") val accountType: String?,
    @JsonProperty("profile_image") val profileImage: String?,
    @JsonProperty("website_url") val websiteUrl: String?,
    @JsonProperty("follower_count") val followerCount: Long?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PinterestTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("refresh_token") val refreshToken: String?,
    @JsonProperty("token_type") val tokenType: String?,
    @JsonProperty("expires_in") val expiresIn: Long,
    val scope: String?,
)
