package com.ongo.domain.approval

interface ApprovalChainRepository {
    fun findByApprovalId(approvalId: Long): List<ApprovalChain>
    fun findById(id: Long): ApprovalChain?
    fun findOverdueSteps(): List<ApprovalChain>
    fun findApproachingDeadlineSteps(withinMinutes: Long): List<ApprovalChain>
    fun save(chain: ApprovalChain): ApprovalChain
    fun update(chain: ApprovalChain): ApprovalChain
    fun deleteByApprovalId(approvalId: Long)
}
