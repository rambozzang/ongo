package com.ongo.application.musicrecommender

import com.ongo.application.musicrecommender.dto.*
import com.ongo.domain.musicrecommender.MusicRecommendation
import com.ongo.domain.musicrecommender.MusicRecommendationRepository
import com.ongo.domain.musicrecommender.MusicTrackRepository
import org.springframework.stereotype.Service

@Service
class MusicRecommenderUseCase(
    private val recommendationRepository: MusicRecommendationRepository,
    private val trackRepository: MusicTrackRepository,
) {

    fun getRecommendations(userId: Long): List<MusicRecommendationResponse> {
        return recommendationRepository.findByUserId(userId).map { it.toResponse() }
    }

    fun getSummary(userId: Long): MusicRecommenderSummaryResponse {
        val recs = recommendationRepository.findByUserId(userId)
        val applied = recs.count { it.status == "APPLIED" }
        return MusicRecommenderSummaryResponse(
            totalRecommendations = recs.size,
            appliedTracks = applied,
            topGenre = "Lo-fi",
            topMood = "편안한",
            avgMatchScore = 89.5,
        )
    }

    private fun MusicRecommendation.toResponse() = MusicRecommendationResponse(
        id = id!!,
        videoId = videoId,
        videoTitle = videoTitle,
        tracks = emptyList(),
        selectedTrackId = selectedTrackId,
        status = status,
        createdAt = createdAt,
    )
}
