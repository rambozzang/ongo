package com.ongo.infrastructure.persistence.approval

import com.ongo.domain.approval.ApprovalComment
import com.ongo.domain.approval.ApprovalCommentRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Repository
@Profile("test")
class InMemoryApprovalCommentRepository : ApprovalCommentRepository {

    private val store = ConcurrentHashMap<Long, ApprovalComment>()
    private val idGenerator = AtomicLong(1)

    override fun findByApprovalId(approvalId: Long): List<ApprovalComment> =
        store.values
            .filter { it.approvalId == approvalId }
            .sortedBy { it.createdAt }

    override fun save(comment: ApprovalComment): ApprovalComment {
        val now = LocalDateTime.now()
        val id = idGenerator.getAndIncrement()
        val saved = comment.copy(
            id = id,
            createdAt = now,
        )
        store[id] = saved
        return saved
    }
}
