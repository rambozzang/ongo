package com.ongo.infrastructure.external.youtube.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// --- Upload ---

data class YouTubeUploadRequest(
    val snippet: Snippet,
    val status: Status,
) {
    data class Snippet(
        val title: String,
        val description: String,
        val tags: List<String>,
        val categoryId: String = "22", // People & Blogs
    )

    data class Status(
        val privacyStatus: String, // public, private, unlisted
        val publishAt: String? = null, // ISO 8601 for scheduled
        val selfDeclaredMadeForKids: Boolean = false,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubeUploadResponse(
    val id: String,
    val snippet: SnippetResponse?,
    val status: StatusResponse?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SnippetResponse(
        val title: String?,
        val channelId: String?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StatusResponse(
        val uploadStatus: String?, // uploaded, processed, rejected, failed
        val privacyStatus: String?,
        val publishAt: String?,
    )
}

// --- Video List / Status ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubeVideoListResponse(
    val items: List<YouTubeVideoItem> = emptyList(),
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class YouTubeVideoItem(
        val id: String,
        val snippet: SnippetDetail?,
        val status: StatusDetail?,
        val statistics: Statistics?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SnippetDetail(
        val title: String?,
        val description: String?,
        val channelId: String?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StatusDetail(
        val uploadStatus: String?,
        val privacyStatus: String?,
        val rejectionReason: String?,
        val failureReason: String?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Statistics(
        val viewCount: String?,
        val likeCount: String?,
        val commentCount: String?,
    )
}

// --- Channel ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubeChannelListResponse(
    val items: List<YouTubeChannelItem> = emptyList(),
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class YouTubeChannelItem(
        val id: String,
        val snippet: ChannelSnippet?,
        val statistics: ChannelStatistics?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ChannelSnippet(
        val title: String?,
        val customUrl: String?,
        val thumbnails: Thumbnails?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Thumbnails(
        val default: ThumbnailDetail?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ThumbnailDetail(
        val url: String?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ChannelStatistics(
        val subscriberCount: String?,
        val viewCount: String?,
        val videoCount: String?,
    )
}

// --- Analytics ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubeAnalyticsResponse(
    val rows: List<List<String>>? = null,
    val columnHeaders: List<ColumnHeader>? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ColumnHeader(
        val name: String?,
        val columnType: String?,
    )
}

// --- OAuth Token ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubeTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("refresh_token") val refreshToken: String?,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("token_type") val tokenType: String?,
)
