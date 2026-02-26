package com.ongo.domain.playlist

import java.time.LocalDateTime

data class PlaylistVideo(
    val id: Long = 0,
    val playlistId: Long,
    val videoId: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val duration: Int = 0,
    val views: Long = 0,
    val position: Int = 0,
    val addedAt: LocalDateTime = LocalDateTime.now(),
)
