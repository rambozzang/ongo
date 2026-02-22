package com.ongo.application.approval

import com.ongo.application.approval.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.approval.Approval
import com.ongo.domain.approval.ApprovalChainRepository
import com.ongo.domain.approval.ApprovalComment
import com.ongo.domain.approval.ApprovalCommentRepository
import com.ongo.domain.approval.ApprovalRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

@Service
class ApprovalUseCase(
    private val approvalRepository: ApprovalRepository,
    private val approvalCommentRepository: ApprovalCommentRepository,
    private val approvalChainRepository: ApprovalChainRepository,
) {

    fun listApprovals(userId: Long, status: String?): ApprovalListResponse {
        val approvals = if (status != null) {
            approvalRepository.findByUserId(userId).filter { it.status == status }
        } else {
            approvalRepository.findByUserId(userId)
        }
        return ApprovalListResponse(
            approvals = approvals.map { it.toResponse() },
            totalCount = approvals.size,
        )
    }

    fun getApproval(userId: Long, id: Long): ApprovalResponse {
        val approval = approvalRepository.findById(id) ?: throw NotFoundException("승인 요청", id)
        if (approval.userId != userId && approval.reviewerId != userId) {
            throw ForbiddenException()
        }
        return approval.toResponse()
    }

    @Transactional
    fun submitForApproval(userId: Long, request: CreateApprovalRequest): ApprovalResponse {
        val approval = Approval(
            userId = userId,
            videoId = request.videoId,
            videoTitle = request.videoTitle,
            platforms = request.platforms,
            scheduledAt = request.scheduledAt,
            requesterId = userId,
            requesterName = request.reviewerName ?: "",
            reviewerId = request.reviewerId,
            reviewerName = request.reviewerName,
            status = "PENDING",
            comment = request.comment,
            requestedAt = LocalDateTime.now(),
        )
        val saved = approvalRepository.save(approval)
        return saved.toResponse()
    }

    @Transactional
    fun updateStatus(userId: Long, id: Long, request: UpdateApprovalStatusRequest): ApprovalResponse {
        val existing = approvalRepository.findById(id) ?: throw NotFoundException("승인 요청", id)
        if (existing.userId != userId && existing.reviewerId != userId) {
            throw ForbiddenException()
        }

        val updated = existing.copy(
            status = request.status,
            comment = request.comment ?: existing.comment,
            revisionNote = request.revisionNote ?: existing.revisionNote,
            reviewerId = if (existing.reviewerId == null) userId else existing.reviewerId,
            decidedAt = LocalDateTime.now(),
        )
        val saved = approvalRepository.update(updated)
        return saved.toResponse()
    }

    fun getComments(userId: Long, approvalId: Long): List<ApprovalCommentResponse> {
        val approval = approvalRepository.findById(approvalId) ?: throw NotFoundException("승인 요청", approvalId)
        if (approval.userId != userId && approval.reviewerId != userId) {
            throw ForbiddenException()
        }
        return approvalCommentRepository.findByApprovalId(approvalId).map { it.toResponse() }
    }

    @Transactional
    fun addComment(userId: Long, userName: String, approvalId: Long, request: CreateApprovalCommentRequest): ApprovalCommentResponse {
        val approval = approvalRepository.findById(approvalId) ?: throw NotFoundException("승인 요청", approvalId)
        if (approval.userId != userId && approval.reviewerId != userId) {
            throw ForbiddenException()
        }

        val comment = ApprovalComment(
            approvalId = approvalId,
            userId = userId,
            userName = userName,
            content = request.content,
            field = request.field,
        )
        val saved = approvalCommentRepository.save(comment)
        return saved.toResponse()
    }

    // ─── Workflow Board ────────────────────────────────────────

    private val STATUS_LABELS = mapOf(
        "DRAFT" to "초안",
        "EDITING" to "편집중",
        "PENDING" to "검수",
        "REVIEW" to "검수",
        "APPROVED" to "승인",
        "PUBLISHED" to "게시",
        "REJECTED" to "거절",
    )

    private val COLUMN_ORDER = listOf("DRAFT", "EDITING", "PENDING", "APPROVED", "PUBLISHED")

    fun getWorkflowBoard(userId: Long): WorkflowBoardResponse {
        val grouped = approvalRepository.findGroupedByStatus(userId)
        val allItems = grouped.values.flatten()

        val columns = COLUMN_ORDER.map { status ->
            val approvals = grouped[status] ?: emptyList()
            WorkflowColumn(
                status = status,
                statusLabel = STATUS_LABELS[status] ?: status,
                items = approvals.map { it.toWorkflowItem() },
                count = approvals.size,
            )
        }

        val decidedApprovals = allItems.filter { it.decidedAt != null && it.requestedAt != null }
        val avgHours = if (decidedApprovals.isNotEmpty()) {
            decidedApprovals.map { Duration.between(it.requestedAt, it.decidedAt).toHours().toDouble() }
                .average()
        } else null

        return WorkflowBoardResponse(
            columns = columns,
            totalItems = allItems.size,
            stats = WorkflowStats(
                totalPending = grouped["PENDING"]?.size ?: 0,
                totalInReview = grouped["REVIEW"]?.size ?: 0,
                totalApproved = grouped["APPROVED"]?.size ?: 0,
                totalRejected = grouped["REJECTED"]?.size ?: 0,
                avgApprovalTimeHours = avgHours,
            ),
        )
    }

    fun getMyTasks(userId: Long): MyTasksResponse {
        val allApprovals = approvalRepository.findByUserId(userId)
        return MyTasksResponse(
            assignedToMe = allApprovals
                .filter { it.reviewerId == userId }
                .map { it.toWorkflowItem() },
            requestedByMe = allApprovals
                .filter { it.requesterId == userId }
                .map { it.toWorkflowItem() },
        )
    }

    fun getPendingReviews(userId: Long): PendingReviewsResponse {
        val reviews = approvalRepository.findByReviewerId(userId)
            .filter { it.status == "PENDING" || it.status == "REVIEW" }

        val now = LocalDateTime.now()
        val overdueCount = reviews.count { approval ->
            val approvalId = approval.id ?: return@count false
            val chains = approvalChainRepository.findByApprovalId(approvalId)
            chains.any { chain ->
                val deadline = chain.deadlineAt
                deadline != null && deadline.isBefore(now) && chain.status == "PENDING"
            }
        }

        return PendingReviewsResponse(
            reviews = reviews.map { it.toWorkflowItem() },
            overdueCount = overdueCount,
        )
    }

    private fun Approval.toWorkflowItem(): WorkflowItem {
        val approvalId = id
        val chains = if (approvalId != null) {
            approvalChainRepository.findByApprovalId(approvalId).map { chain ->
                WorkflowStepInfo(
                    stepOrder = chain.stepOrder,
                    stepName = "단계 ${chain.stepOrder}",
                    reviewerName = chain.approverName,
                    status = chain.status,
                    completedAt = chain.approvedAt?.toString(),
                )
            }
        } else null

        return WorkflowItem(
            approvalId = id!!,
            videoId = videoId,
            videoTitle = videoTitle,
            platforms = platforms.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            requesterName = requesterName,
            reviewerName = reviewerName,
            scheduledAt = scheduledAt?.toString(),
            status = status,
            requestedAt = requestedAt.toString(),
            updatedAt = updatedAt?.toString(),
            chainSteps = chains?.ifEmpty { null },
        )
    }

    private fun Approval.toResponse() = ApprovalResponse(
        id = id!!,
        userId = userId,
        videoId = videoId,
        videoTitle = videoTitle,
        platforms = platforms,
        scheduledAt = scheduledAt,
        requesterId = requesterId,
        requesterName = requesterName,
        reviewerId = reviewerId,
        reviewerName = reviewerName,
        status = status,
        comment = comment,
        revisionNote = revisionNote,
        requestedAt = requestedAt,
        decidedAt = decidedAt,
        createdAt = createdAt,
    )

    private fun ApprovalComment.toResponse() = ApprovalCommentResponse(
        id = id!!,
        approvalId = approvalId,
        userId = userId,
        userName = userName,
        content = content,
        field = field,
        createdAt = createdAt,
    )
}
