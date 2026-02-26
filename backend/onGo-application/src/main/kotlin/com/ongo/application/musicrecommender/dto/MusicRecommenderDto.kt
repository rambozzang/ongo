package com.ongo.application.musicrecommender.dto

import java.time.LocalDateTime

data class MusicTrackResponse(
    val id: Long,
    val title: String,
    val artist: String,
    val genre: String,
    val mood: String,
    val bpm: Int,
    val duration: Int,
    val previewUrl: String?,
    val licenseType: String,
    val matchScore: Int,
)

data class MusicRecommendationResponse(
    val id: Long,
    val videoId: Long,
    val videoTitle: String,
    val tracks: List<MusicTrackResponse>,
    val selectedTrackId: Long?,
    val status: String,
    val createdAt: LocalDateTime?,
)

data class MusicRecommenderSummaryResponse(
    val totalRecommendations: Int,
    val appliedTracks: Int,
    val topGenre: String,
    val topMood: String,
    val avgMatchScore: Double,
)

data class RecommendRequest(
    val videoId: Long,
    val mood: String? = null,
)
