package com.ongo.domain.musicrecommender

interface MusicRecommendationRepository {
    fun findById(id: Long): MusicRecommendation?
    fun findByUserId(userId: Long): List<MusicRecommendation>
    fun save(recommendation: MusicRecommendation): MusicRecommendation
    fun updateStatus(id: Long, status: String)
}
