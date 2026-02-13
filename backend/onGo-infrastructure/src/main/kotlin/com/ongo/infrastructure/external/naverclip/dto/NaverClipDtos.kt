package com.ongo.infrastructure.external.naverclip.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// --- Upload Init ---

data class NaverClipUploadInitRequest(
    val title: String,
    val description: String,
    val tags: List<String>,
    @JsonProperty("file_size") val fileSize: Long,
    val visibility: String, // PUBLIC, PRIVATE
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverClipUploadInitResponse(
    @JsonProperty("upload_id") val uploadId: String?,
    @JsonProperty("upload_url") val uploadUrl: String?,
    val error: NaverClipError?,
)

// --- Upload Complete ---

data class NaverClipUploadCompleteRequest(
    @JsonProperty("upload_id") val uploadId: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverClipUploadCompleteResponse(
    @JsonProperty("clip_id") val clipId: String?,
    @JsonProperty("clip_url") val clipUrl: String?,
    val status: String?,
    val error: NaverClipError?,
)

// --- Clip Status ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverClipStatusResponse(
    @JsonProperty("clip_id") val clipId: String?,
    val status: String?, // PROCESSING, PUBLISHED, FAILED
    @JsonProperty("clip_url") val clipUrl: String?,
    @JsonProperty("error_message") val errorMessage: String?,
    val error: NaverClipError?,
)

// --- Clip Statistics ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverClipStatisticsResponse(
    @JsonProperty("clip_id") val clipId: String?,
    @JsonProperty("view_count") val viewCount: Long?,
    @JsonProperty("like_count") val likeCount: Long?,
    @JsonProperty("comment_count") val commentCount: Long?,
    @JsonProperty("share_count") val shareCount: Long?,
    @JsonProperty("watch_time_seconds") val watchTimeSeconds: Long?,
    val error: NaverClipError?,
)

// --- Channel Info ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverClipChannelResponse(
    @JsonProperty("channel_id") val channelId: String?,
    @JsonProperty("channel_name") val channelName: String?,
    @JsonProperty("channel_url") val channelUrl: String?,
    @JsonProperty("subscriber_count") val subscriberCount: Long?,
    @JsonProperty("profile_image_url") val profileImageUrl: String?,
    val error: NaverClipError?,
)

// --- OAuth Token ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverClipTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("refresh_token") val refreshToken: String?,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("token_type") val tokenType: String?,
)

// --- Common Error ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverClipError(
    val code: String?,
    val message: String?,
)
