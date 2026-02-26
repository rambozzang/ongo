package com.ongo.application.contentseries.dto

import java.time.LocalDateTime

data class CreateSeriesRequest(
    val title: String,
    val description: String? = null,
    val platform: String = "YOUTUBE",
    val frequency: String = "WEEKLY",
    val customFrequencyDays: Int? = null,
    val tags: List<String> = emptyList(),
)

data class UpdateSeriesRequest(
    val title: String? = null,
    val description: String? = null,
    val status: String? = null,
    val frequency: String? = null,
    val tags: List<String>? = null,
)

data class AddEpisodeRequest(
    val title: String,
    val videoId: String? = null,
    val platform: String = "YOUTUBE",
    val status: String = "PLANNED",
    val scheduledDate: LocalDateTime? = null,
)

data class UpdateEpisodeRequest(
    val title: String? = null,
    val videoId: String? = null,
    val status: String? = null,
    val scheduledDate: LocalDateTime? = null,
)

data class ContentSeriesResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val coverImageUrl: String?,
    val status: String,
    val platform: String,
    val frequency: String,
    val totalEpisodes: Int,
    val publishedEpisodes: Int,
    val avgViews: Long,
    val totalViews: Long,
    val episodes: List<SeriesEpisodeResponse>,
    val tags: String,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class SeriesEpisodeResponse(
    val id: Long,
    val seriesId: Long,
    val episodeNumber: Int,
    val title: String,
    val videoId: String?,
    val platform: String,
    val status: String,
    val scheduledDate: LocalDateTime?,
    val publishedDate: LocalDateTime?,
    val views: Long,
    val likes: Long,
    val comments: Long,
)

data class SeriesAnalyticsResponse(
    val seriesId: Long,
    val viewsTrend: String,
    val subscriberGrowth: Int,
    val avgEngagement: Double,
    val bestPerformingEpisode: SeriesEpisodeResponse?,
    val dropOffRate: Double,
    val audienceReturnRate: Double,
)
