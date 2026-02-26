package com.ongo.domain.thumbnailabtest

interface ThumbnailAbTestRepository {
    fun findByWorkspaceId(workspaceId: Long): List<ThumbnailAbTest>
    fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<ThumbnailAbTest>
    fun findById(id: Long): ThumbnailAbTest?
    fun save(test: ThumbnailAbTest): ThumbnailAbTest
    fun updateStatus(id: Long, status: String, winner: String?)
}
