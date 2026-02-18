package com.ongo.infrastructure.external.tiktok.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// --- Creator Info ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class TikTokCreatorInfoResponse(
    val data: CreatorData?,
    val error: TikTokError?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CreatorData(
        @JsonProperty("creator_avatar_url") val creatorAvatarUrl: String?,
        @JsonProperty("creator_username") val creatorUsername: String?,
        @JsonProperty("creator_nickname") val creatorNickname: String?,
        @JsonProperty("follower_count") val followerCount: Long?,
    )
}

// --- Video Init Upload ---

data class TikTokInitUploadRequest(
    @JsonProperty("post_info") val postInfo: PostInfo,
    @JsonProperty("source_info") val sourceInfo: SourceInfo,
) {
    data class PostInfo(
        val title: String,
        @JsonProperty("privacy_level") val privacyLevel: String, // PUBLIC_TO_EVERYONE, MUTUAL_FOLLOW_FRIENDS, SELF_ONLY
        @JsonProperty("disable_duet") val disableDuet: Boolean = false,
        @JsonProperty("disable_comment") val disableComment: Boolean = false,
        @JsonProperty("disable_stitch") val disableStitch: Boolean = false,
        @JsonProperty("video_cover_timestamp_ms") val videoCoverTimestampMs: Long = 0,
    )

    data class SourceInfo(
        val source: String = "FILE_UPLOAD",
        @JsonProperty("video_size") val videoSize: Long,
        @JsonProperty("chunk_size") val chunkSize: Long,
        @JsonProperty("total_chunk_count") val totalChunkCount: Int,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TikTokInitUploadResponse(
    val data: UploadData?,
    val error: TikTokError?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UploadData(
        @JsonProperty("publish_id") val publishId: String?,
        @JsonProperty("upload_url") val uploadUrl: String?,
    )
}

// --- Video Publish Status ---

data class TikTokPublishStatusRequest(
    @JsonProperty("publish_id") val publishId: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TikTokPublishStatusResponse(
    val data: StatusData?,
    val error: TikTokError?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StatusData(
        val status: String?, // PROCESSING_UPLOAD, PROCESSING_DOWNLOAD, SEND_TO_USER_INBOX, PUBLISH_COMPLETE, FAILED
        @JsonProperty("publicaly_available_post_id") val publicPostId: List<String>?,
        @JsonProperty("fail_reason") val failReason: String?,
    )
}

// --- Video Query ---

data class TikTokVideoQueryRequest(
    val filters: Filters,
    val fields: List<String>,
) {
    data class Filters(
        val ids: List<String>,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TikTokVideoQueryResponse(
    val data: VideoQueryData?,
    val error: TikTokError?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class VideoQueryData(
        val videos: List<TikTokVideo>?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TikTokVideo(
        val id: String?,
        val title: String?,
        @JsonProperty("view_count") val viewCount: Long?,
        @JsonProperty("like_count") val likeCount: Long?,
        @JsonProperty("comment_count") val commentCount: Long?,
        @JsonProperty("share_count") val shareCount: Long?,
        @JsonProperty("create_time") val createTime: Long?,
    )
}

// --- OAuth Token ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class TikTokTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("refresh_token") val refreshToken: String?,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("open_id") val openId: String?,
    @JsonProperty("token_type") val tokenType: String?,
)

// --- Comment API ---

data class TikTokCommentListRequest(
    @JsonProperty("video_id") val videoId: String,
    @JsonProperty("max_count") val maxCount: Int = 50,
    val cursor: Long? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TikTokCommentListResponse(
    val data: CommentListData?,
    val error: TikTokError?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CommentListData(
        val comments: List<TikTokComment>?,
        val cursor: Long?,
        @JsonProperty("has_more") val hasMore: Boolean?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TikTokComment(
        val id: String?,
        val text: String?,
        @JsonProperty("like_count") val likeCount: Int?,
        @JsonProperty("reply_count") val replyCount: Int?,
        @JsonProperty("create_time") val createTime: Long?,
        @JsonProperty("parent_comment_id") val parentCommentId: String?,
        val user: CommentUser?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CommentUser(
        @JsonProperty("display_name") val displayName: String?,
        @JsonProperty("avatar_url") val avatarUrl: String?,
        @JsonProperty("profile_deep_link") val profileDeepLink: String?,
    )
}

// --- Common Error ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class TikTokError(
    val code: String?,
    val message: String?,
    @JsonProperty("log_id") val logId: String?,
)
