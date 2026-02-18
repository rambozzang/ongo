package com.ongo.infrastructure.external.tumblr.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

data class TumblrNpfPostRequest(
    val content: List<ContentBlock>,
    val layout: List<LayoutBlock> = emptyList(),
    val tags: String? = null, // comma-separated
    val state: String = "published", // published, draft, queue, private
) {
    data class ContentBlock(
        val type: String, // video, text
        val url: String? = null, // for video type
        val text: String? = null, // for text type
    )

    data class LayoutBlock(
        val type: String = "rows",
        val display: List<Display>? = null,
    ) {
        data class Display(
            val blocks: List<Int>,
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TumblrPostResponse(
    val response: PostData?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PostData(
        val id: Long?,
        @JsonProperty("id_string") val idString: String?,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TumblrGetPostResponse(
    val response: PostDetail?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PostDetail(
        val id: Long?,
        @JsonProperty("id_string") val idString: String?,
        @JsonProperty("post_url") val postUrl: String?,
        val state: String?,
        @JsonProperty("note_count") val noteCount: Long?,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TumblrNotesResponse(
    val response: NotesData?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class NotesData(
        val notes: List<Note>?,
        @JsonProperty("total_notes") val totalNotes: Long?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Note(
        val type: String?, // like, reblog, reply
        @JsonProperty("blog_name") val blogName: String? = null,
        @JsonProperty("blog_url") val blogUrl: String? = null,
        @JsonProperty("avatar_shape") val avatarShape: String? = null,
        @JsonProperty("reply_text") val replyText: String? = null,
        val timestamp: Long? = null,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TumblrUserInfoResponse(
    val response: UserResponse?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UserResponse(
        val user: UserData?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UserData(
        val name: String?,
        val blogs: List<BlogInfo>?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BlogInfo(
        val name: String?,
        val url: String?,
        val title: String?,
        val followers: Long?,
        val avatar: List<AvatarInfo>?,
        val primary: Boolean?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AvatarInfo(
        val url: String?,
        val width: Int?,
        val height: Int?,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TumblrTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("refresh_token") val refreshToken: String?,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("token_type") val tokenType: String?,
    val scope: String?,
)
