package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.musicrecommender.*
import org.springframework.stereotype.Repository

@Repository
class MusicRecommendationStubRepository : MusicRecommendationRepository {
    override fun findById(id: Long): MusicRecommendation? = null
    override fun findByUserId(userId: Long): List<MusicRecommendation> = emptyList()
    override fun save(recommendation: MusicRecommendation): MusicRecommendation = recommendation.copy(id = 1)
    override fun updateStatus(id: Long, status: String) {}
}

@Repository
class MusicTrackStubRepository : MusicTrackRepository {
    override fun findById(id: Long): MusicTrack? = null
    override fun findByGenre(genre: String): List<MusicTrack> = emptyList()
    override fun findByMood(mood: String): List<MusicTrack> = emptyList()
    override fun save(track: MusicTrack): MusicTrack = track.copy(id = 1)
}
