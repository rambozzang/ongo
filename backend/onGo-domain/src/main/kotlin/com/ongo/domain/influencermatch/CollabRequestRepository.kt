package com.ongo.domain.influencermatch

interface CollabRequestRepository {
    fun findById(id: Long): CollabRequest?
    fun findByUserId(userId: Long): List<CollabRequest>
    fun save(request: CollabRequest): CollabRequest
    fun update(request: CollabRequest): CollabRequest
    fun delete(id: Long)
}
