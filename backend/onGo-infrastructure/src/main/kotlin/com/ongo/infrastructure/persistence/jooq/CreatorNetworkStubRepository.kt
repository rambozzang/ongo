package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.creatornetwork.*
import org.springframework.stereotype.Repository

@Repository
class CreatorProfileStubRepository : CreatorProfileRepository {
    override fun findByUserId(userId: Long): List<CreatorProfile> = emptyList()
    override fun findById(id: Long): CreatorProfile? = null
    override fun findByCategory(category: String): List<CreatorProfile> = emptyList()
    override fun save(profile: CreatorProfile): CreatorProfile = profile.copy(id = 1)
}

@Repository
class CollaborationRequestStubRepository : CollaborationRequestRepository {
    override fun findByCreatorId(creatorId: Long): List<CollaborationRequest> = emptyList()
    override fun findById(id: Long): CollaborationRequest? = null
    override fun save(request: CollaborationRequest): CollaborationRequest = request.copy(id = 1)
    override fun updateStatus(id: Long, status: String) {}
}
