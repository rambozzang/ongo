package com.ongo.domain.contentseries

import java.time.LocalDateTime

data class SeriesEpisode(
    val id: Long? = null,
    val seriesId: Long,
    val episodeNumber: Int = 1,
    val title: String,
    val videoId: String? = null,
    val platform: String = "YOUTUBE",
    val status: String = "PLANNED",
    val scheduledDate: LocalDateTime? = null,
    val publishedDate: LocalDateTime? = null,
    val views: Long = 0,
    val likes: Long = 0,
    val comments: Long = 0,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
