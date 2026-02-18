package com.ongo.infrastructure.external.vimeo.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// --- Upload (Pull approach) ---
data class VimeoUploadRequest(
    val upload: Upload,
    val name: String,
    val description: String,
    val privacy: Privacy,
) {
    data class Upload(
        val approach: String = "pull",
        val link: String, // URL to pull video from
    )
    data class Privacy(
        val view: String, // anybody, nobody, unlisted
        val embed: String = "public",
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class VimeoVideoResponse(
    val uri: String, // /videos/{id}
    val name: String?,
    val link: String?,
    val status: String?, // available, uploading, transcoding, etc
    val upload: UploadStatus?,
    val stats: Stats?,
    val metadata: Metadata?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UploadStatus(
        val status: String?, // complete, in_progress, error
    )
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Stats(
        val plays: Long?,
    )
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Metadata(
        val connections: Connections?,
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Connections(
            val likes: CountInfo?,
            val comments: CountInfo?,
        ) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            data class CountInfo(
                val total: Long?,
            )
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class VimeoUserResponse(
    val uri: String,
    val name: String?,
    val link: String?,
    @JsonProperty("pictures") val pictures: Pictures?,
    val metadata: UserMetadata?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Pictures(
        val sizes: List<PictureSize>?,
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class PictureSize(
            val link: String?,
            val width: Int?,
        )
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UserMetadata(
        val connections: UserConnections?,
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class UserConnections(
            val followers: CountInfo?,
        ) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            data class CountInfo(
                val total: Long?,
            )
        }
    }
}

// --- OAuth Token ---
@JsonIgnoreProperties(ignoreUnknown = true)
data class VimeoTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("token_type") val tokenType: String?,
    @JsonProperty("scope") val scope: String?,
    @JsonProperty("refresh_token") val refreshToken: String?,
    @JsonProperty("expires_in") val expiresIn: Long? = null,
)

// --- Comment API ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class VimeoCommentsResponse(
    val total: Int? = null,
    val page: Int? = null,
    @JsonProperty("per_page") val perPage: Int? = null,
    val data: List<VimeoCommentData>? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class VimeoCommentData(
        val uri: String? = null,
        val text: String? = null,
        @JsonProperty("created_on") val createdOn: String? = null,
        val user: VimeoCommentUser? = null,
        val metadata: CommentMetadata? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class VimeoCommentUser(
        val name: String? = null,
        val link: String? = null,
        val pictures: VimeoPictures? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class VimeoPictures(
        val sizes: List<VimeoPictureSize>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class VimeoPictureSize(
        val link: String? = null,
        val width: Int? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CommentMetadata(
        val connections: CommentConnections? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CommentConnections(
        val replies: ConnectionInfo? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ConnectionInfo(
        val total: Int? = null,
    )
}

data class VimeoCommentRequest(
    val text: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VimeoCommentResponse(
    val uri: String? = null,
)
