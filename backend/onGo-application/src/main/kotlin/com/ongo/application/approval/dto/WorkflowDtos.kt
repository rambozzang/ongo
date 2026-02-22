package com.ongo.application.approval.dto

data class WorkflowBoardResponse(
    val columns: List<WorkflowColumn>,
    val totalItems: Int,
    val stats: WorkflowStats,
)

data class WorkflowColumn(
    val status: String,
    val statusLabel: String,
    val items: List<WorkflowItem>,
    val count: Int,
)

data class WorkflowItem(
    val approvalId: Long,
    val videoId: Long,
    val videoTitle: String,
    val platforms: List<String>,
    val requesterName: String,
    val reviewerName: String?,
    val scheduledAt: String?,
    val status: String,
    val requestedAt: String,
    val updatedAt: String?,
    val chainSteps: List<WorkflowStepInfo>?,
)

data class WorkflowStepInfo(
    val stepOrder: Int,
    val stepName: String,
    val reviewerName: String?,
    val status: String,
    val completedAt: String?,
)

data class WorkflowStats(
    val totalPending: Int,
    val totalInReview: Int,
    val totalApproved: Int,
    val totalRejected: Int,
    val avgApprovalTimeHours: Double?,
)

data class MyTasksResponse(
    val assignedToMe: List<WorkflowItem>,
    val requestedByMe: List<WorkflowItem>,
)

data class PendingReviewsResponse(
    val reviews: List<WorkflowItem>,
    val overdueCount: Int,
)
