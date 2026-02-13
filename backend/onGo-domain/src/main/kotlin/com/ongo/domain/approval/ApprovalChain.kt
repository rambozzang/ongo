package com.ongo.domain.approval

import java.time.LocalDateTime

data class ApprovalChain(
    val id: Long? = null,
    val approvalId: Long,
    val stepOrder: Int,
    val approverId: Long,
    val approverName: String,
    val status: String = "PENDING",
    val deadlineAt: LocalDateTime? = null,
    val approvedAt: LocalDateTime? = null,
    val comment: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
