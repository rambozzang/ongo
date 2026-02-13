package com.ongo.domain.approval

interface ApprovalCommentRepository {
    fun findByApprovalId(approvalId: Long): List<ApprovalComment>
    fun save(comment: ApprovalComment): ApprovalComment
}
