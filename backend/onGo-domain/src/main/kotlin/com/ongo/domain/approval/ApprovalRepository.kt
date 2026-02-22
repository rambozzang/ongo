package com.ongo.domain.approval

interface ApprovalRepository {
    fun findById(id: Long): Approval?
    fun findByUserId(userId: Long): List<Approval>
    fun findByReviewerId(reviewerId: Long): List<Approval>
    fun findByStatus(status: String): List<Approval>
    fun findGroupedByStatus(userId: Long): Map<String, List<Approval>>
    fun save(approval: Approval): Approval
    fun update(approval: Approval): Approval
    fun delete(id: Long)
}
