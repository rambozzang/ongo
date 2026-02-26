package com.ongo.domain.playlist

import java.time.LocalDateTime

data class Playlist(
    val id: Long = 0,
    val userId: Long,
    val title: String,
    val description: String? = null,
    val platform: String,
    val platformPlaylistId: String? = null,
    val visibility: String = "PUBLIC",
    val thumbnailUrl: String? = null,
    val videoCount: Int = 0,
    val totalViews: Long = 0,
    val totalDuration: Int = 0,
    val tags: List<String> = emptyList(),
    val isSynced: Boolean = false,
    val lastSyncedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
