package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.influencermatch.*
import org.springframework.stereotype.Repository

@Repository
class InfluencerProfileStubRepository : InfluencerProfileRepository {
    override fun findById(id: Long): InfluencerProfile? = null
    override fun findByUserId(userId: Long): List<InfluencerProfile> = emptyList()
    override fun save(profile: InfluencerProfile): InfluencerProfile = profile.copy(id = 1)
    override fun update(profile: InfluencerProfile): InfluencerProfile = profile
    override fun delete(id: Long) {}
}

@Repository
class CollabRequestStubRepository : CollabRequestRepository {
    override fun findById(id: Long): CollabRequest? = null
    override fun findByUserId(userId: Long): List<CollabRequest> = emptyList()
    override fun save(request: CollabRequest): CollabRequest = request.copy(id = 1)
    override fun update(request: CollabRequest): CollabRequest = request
    override fun delete(id: Long) {}
}
