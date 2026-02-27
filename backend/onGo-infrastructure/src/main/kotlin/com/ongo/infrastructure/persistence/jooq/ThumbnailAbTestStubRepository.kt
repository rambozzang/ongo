package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.thumbnailabtest.*
import org.springframework.stereotype.Repository

@Repository
class ThumbnailAbTestStubRepository : ThumbnailAbTestRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<ThumbnailAbTest> = emptyList()
    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<ThumbnailAbTest> = emptyList()
    override fun findById(id: Long): ThumbnailAbTest? = null
    override fun save(test: ThumbnailAbTest): ThumbnailAbTest = test.copy(id = 1)
    override fun updateStatus(id: Long, status: String, winner: String?) {}
}
