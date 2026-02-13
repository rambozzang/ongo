package com.ongo.application.approval.dto

import java.time.LocalDateTime

data class ApprovalResponse(
    val id: Long,
    val userId: Long,
    val videoId: Long,
    val videoTitle: String,
    val platforms: String,
    val scheduledAt: LocalDateTime?,
    val requesterId: Long,
    val requesterName: String,
    val reviewerId: Long?,
    val reviewerName: String?,
    val status: String,
    val comment: String?,
    val revisionNote: String?,
    val requestedAt: LocalDateTime,
    val decidedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
)

data class ApprovalCommentResponse(
    val id: Long,
    val approvalId: Long,
    val userId: Long,
    val userName: String,
    val content: String,
    val field: String?,
    val createdAt: LocalDateTime?,
)

data class ApprovalListResponse(
    val approvals: List<ApprovalResponse>,
    val totalCount: Int,
)

data class CreateApprovalRequest(
    val videoId: Long,
    val videoTitle: String,
    val platforms: String,
    val scheduledAt: LocalDateTime? = null,
    val reviewerId: Long? = null,
    val reviewerName: String? = null,
    val comment: String? = null,
)

data class UpdateApprovalStatusRequest(
    val status: String,
    val comment: String? = null,
    val revisionNote: String? = null,
)

data class CreateApprovalCommentRequest(
    val content: String,
    val field: String? = null,
)

// Approval Chain DTOs

data class ApprovalChainResponse(
    val id: Long,
    val approvalId: Long,
    val stepOrder: Int,
    val approverId: Long,
    val approverName: String,
    val status: String,
    val deadlineAt: LocalDateTime?,
    val approvedAt: LocalDateTime?,
    val comment: String?,
    val createdAt: LocalDateTime?,
)

data class CreateApprovalChainRequest(
    val steps: List<ChainStepRequest>,
    val deadlineHours: Long? = 24,
)

data class ChainStepRequest(
    val approverId: Long,
    val approverName: String,
    val deadlineHours: Long? = null,
)

data class ApprovalChainSlaResponse(
    val stepId: Long,
    val stepOrder: Int,
    val approverName: String,
    val status: String,
    val deadlineAt: LocalDateTime?,
    val remainingMinutes: Long?,
    val isOverdue: Boolean,
)

data class ApproveStepRequest(
    val comment: String? = null,
)
