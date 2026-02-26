package com.ongo.domain.influencermatch

interface InfluencerProfileRepository {
    fun findById(id: Long): InfluencerProfile?
    fun findByUserId(userId: Long): List<InfluencerProfile>
    fun save(profile: InfluencerProfile): InfluencerProfile
    fun update(profile: InfluencerProfile): InfluencerProfile
    fun delete(id: Long)
}
