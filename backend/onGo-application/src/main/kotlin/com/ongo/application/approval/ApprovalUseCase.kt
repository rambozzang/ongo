package com.ongo.application.approval

import com.ongo.application.approval.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.approval.Approval
import com.ongo.domain.approval.ApprovalComment
import com.ongo.domain.approval.ApprovalCommentRepository
import com.ongo.domain.approval.ApprovalRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ApprovalUseCase(
    private val approvalRepository: ApprovalRepository,
    private val approvalCommentRepository: ApprovalCommentRepository,
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
