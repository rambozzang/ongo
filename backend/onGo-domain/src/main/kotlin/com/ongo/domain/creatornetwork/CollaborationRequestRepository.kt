package com.ongo.domain.creatornetwork

interface CollaborationRequestRepository {
    fun findByCreatorId(creatorId: Long): List<CollaborationRequest>
    fun findById(id: Long): CollaborationRequest?
    fun save(request: CollaborationRequest): CollaborationRequest
    fun updateStatus(id: Long, status: String)
}
