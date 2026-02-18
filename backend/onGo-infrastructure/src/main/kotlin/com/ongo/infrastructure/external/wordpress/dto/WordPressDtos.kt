package com.ongo.infrastructure.external.wordpress.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class WordPressMediaResponse(
    @JsonProperty("ID") val id: Long,
    @JsonProperty("URL") val url: String?,
    val guid: String?,
    @JsonProperty("mime_type") val mimeType: String?,
)

data class WordPressPostRequest(
    val title: String,
    val content: String,
    val status: String = "publish", // publish, draft, private
    val tags: List<String> = emptyList(),
    val format: String = "video",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WordPressPostResponse(
    @JsonProperty("ID") val id: Long,
    @JsonProperty("URL") val url: String?,
    val title: String?,
    val status: String?,
    @JsonProperty("short_URL") val shortUrl: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WordPressPostStatsResponse(
    val views: Long?,
    val likes: Long?,
    val comments: Long?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WordPressMeResponse(
    @JsonProperty("ID") val id: Long,
    @JsonProperty("display_name") val displayName: String?,
    @JsonProperty("username") val username: String?,
    @JsonProperty("avatar_URL") val avatarUrl: String?,
    @JsonProperty("primary_blog_url") val primaryBlogUrl: String?,
    @JsonProperty("primary_blog") val primaryBlog: PrimaryBlog?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PrimaryBlog(
        @JsonProperty("ID") val id: Long?,
        @JsonProperty("URL") val url: String?,
        val name: String?,
        val subscribers_count: Long?,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class WordPressTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("token_type") val tokenType: String?,
    @JsonProperty("blog_id") val blogId: String?,
    @JsonProperty("blog_url") val blogUrl: String?,
)

// --- Comment DTOs ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class WordPressCommentsResponse(
    val found: Int? = null,
    val comments: List<WordPressCommentData>? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class WordPressCommentData(
        @JsonProperty("ID") val id: String? = null,
        val author: WordPressAuthor? = null,
        val content: String? = null,
        @JsonProperty("like_count") val likeCount: Int? = null,
        val date: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class WordPressAuthor(
        @JsonProperty("ID") val id: String? = null,
        val name: String? = null,
        @JsonProperty("avatar_URL") val avatarUrl: String? = null,
        @JsonProperty("URL") val url: String? = null,
    )
}

data class WordPressCommentRequest(
    val content: String,
    @JsonProperty("parent") val parentId: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WordPressCommentResponse(
    @JsonProperty("ID") val id: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WordPressDeleteResponse(
    val status: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WordPressLikeResponse(
    val success: Boolean? = null,
    @JsonProperty("like_count") val likeCount: Int? = null,
)
