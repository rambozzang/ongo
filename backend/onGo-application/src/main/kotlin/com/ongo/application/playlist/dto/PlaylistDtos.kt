package com.ongo.application.playlist.dto

data class PlaylistResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val platform: String,
    val platformPlaylistId: String?,
    val visibility: String,
    val thumbnailUrl: String?,
    val videoCount: Int,
    val totalViews: Long,
    val totalDuration: Int,
    val tags: List<String>,
    val isSynced: Boolean,
    val lastSyncedAt: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class PlaylistVideoResponse(
    val id: Long,
    val playlistId: Long,
    val videoId: String,
    val title: String,
    val thumbnailUrl: String?,
    val duration: Int,
    val views: Long,
    val position: Int,
    val addedAt: String,
)

data class CreatePlaylistRequest(
    val title: String,
    val description: String? = null,
    val platform: String,
    val visibility: String = "PUBLIC",
    val tags: List<String>? = null,
)

data class UpdatePlaylistRequest(
    val title: String? = null,
    val description: String? = null,
    val visibility: String? = null,
    val tags: List<String>? = null,
)

data class AddVideoRequest(
    val videoId: String,
    val position: Int? = null,
)

data class ReorderVideosRequest(
    val videoIds: List<String>,
)

data class PlaylistSummaryResponse(
    val totalPlaylists: Int,
    val totalVideos: Int,
    val totalViews: Long,
    val platformBreakdown: List<PlatformCount>,
)

data class PlatformCount(
    val platform: String,
    val count: Int,
)
