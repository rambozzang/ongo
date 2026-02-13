package com.ongo.infrastructure.persistence.approval

import com.ongo.domain.approval.Approval
import com.ongo.domain.approval.ApprovalRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Repository
@Profile("test")
class InMemoryApprovalRepository : ApprovalRepository {

    private val store = ConcurrentHashMap<Long, Approval>()
    private val idGenerator = AtomicLong(1)

    override fun findById(id: Long): Approval? = store[id]

    override fun findByUserId(userId: Long): List<Approval> =
        store.values
            .filter { it.userId == userId || it.requesterId == userId || it.reviewerId == userId }
            .sortedByDescending { it.createdAt }

    override fun findByReviewerId(reviewerId: Long): List<Approval> =
        store.values
            .filter { it.reviewerId == reviewerId }
            .sortedByDescending { it.createdAt }

    override fun findByStatus(status: String): List<Approval> =
        store.values
            .filter { it.status == status }
            .sortedByDescending { it.createdAt }

    override fun save(approval: Approval): Approval {
        val now = LocalDateTime.now()
        val id = idGenerator.getAndIncrement()
        val saved = approval.copy(
            id = id,
            createdAt = now,
            updatedAt = now,
        )
        store[id] = saved
        return saved
    }

    override fun update(approval: Approval): Approval {
        val now = LocalDateTime.now()
        val updated = approval.copy(updatedAt = now)
        store[approval.id!!] = updated
        return updated
    }

    override fun delete(id: Long) {
        store.remove(id)
    }
}
