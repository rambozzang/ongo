package com.ongo.application.approval

import com.ongo.application.approval.dto.*
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.approval.ApprovalChain
import com.ongo.domain.approval.ApprovalChainRepository
import com.ongo.domain.approval.ApprovalRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ApprovalChainUseCase(
    private val approvalRepository: ApprovalRepository,
    private val approvalChainRepository: ApprovalChainRepository,
) {

    fun getChainSteps(userId: Long, approvalId: Long): List<ApprovalChainResponse> {
        val approval = approvalRepository.findById(approvalId) ?: throw NotFoundException("승인 요청", approvalId)
        if (approval.userId != userId && approval.reviewerId != userId) {
            throw ForbiddenException()
        }
        return approvalChainRepository.findByApprovalId(approvalId).map { it.toResponse() }
    }

    @Transactional
    fun createChain(userId: Long, approvalId: Long, request: CreateApprovalChainRequest): List<ApprovalChainResponse> {
        val approval = approvalRepository.findById(approvalId) ?: throw NotFoundException("승인 요청", approvalId)
        if (approval.userId != userId && approval.requesterId != userId) {
            throw ForbiddenException()
        }

        // Clear any existing chain
        approvalChainRepository.deleteByApprovalId(approvalId)

        val defaultDeadlineHours = request.deadlineHours ?: 24L

        val chains = request.steps.mapIndexed { index, step ->
            val chain = ApprovalChain(
                approvalId = approvalId,
                stepOrder = index + 1,
                approverId = step.approverId,
                approverName = step.approverName,
                status = if (index == 0) "PENDING" else "WAITING",
                deadlineAt = if (index == 0) {
                    LocalDateTime.now().plusHours(step.deadlineHours ?: defaultDeadlineHours)
                } else null,
            )
            approvalChainRepository.save(chain)
        }

        return chains.map { it.toResponse() }
    }

    @Transactional
    fun approveStep(userId: Long, approvalId: Long, stepId: Long, comment: String?): ApprovalChainResponse {
        val step = approvalChainRepository.findById(stepId) ?: throw NotFoundException("승인 단계", stepId)
        if (step.approverId != userId) {
            throw ForbiddenException("이 단계의 승인 권한이 없습니다")
        }
        if (step.status != "PENDING") {
            throw BusinessException("INVALID_STATUS", "이미 처리된 단계입니다")
        }

        val updated = approvalChainRepository.update(step.copy(
            status = "APPROVED",
            approvedAt = LocalDateTime.now(),
            comment = comment,
        ))

        // Activate next step
        val allSteps = approvalChainRepository.findByApprovalId(approvalId)
        val nextStep = allSteps.find { it.stepOrder == step.stepOrder + 1 && it.status == "WAITING" }

        if (nextStep != null) {
            approvalChainRepository.update(nextStep.copy(
                status = "PENDING",
                deadlineAt = LocalDateTime.now().plusHours(24),
            ))
        } else {
            // All steps approved, update approval status
            val approval = approvalRepository.findById(approvalId)
            if (approval != null) {
                approvalRepository.update(approval.copy(
                    status = "APPROVED",
                    decidedAt = LocalDateTime.now(),
                ))
            }
        }

        return updated.toResponse()
    }

    @Transactional
    fun rejectStep(userId: Long, approvalId: Long, stepId: Long, comment: String?): ApprovalChainResponse {
        val step = approvalChainRepository.findById(stepId) ?: throw NotFoundException("승인 단계", stepId)
        if (step.approverId != userId) {
            throw ForbiddenException("이 단계의 승인 권한이 없습니다")
        }
        if (step.status != "PENDING") {
            throw BusinessException("INVALID_STATUS", "이미 처리된 단계입니다")
        }

        val updated = approvalChainRepository.update(step.copy(
            status = "REJECTED",
            approvedAt = LocalDateTime.now(),
            comment = comment,
        ))

        // Update approval to rejected
        val approval = approvalRepository.findById(approvalId)
        if (approval != null) {
            approvalRepository.update(approval.copy(
                status = "REJECTED",
                decidedAt = LocalDateTime.now(),
            ))
        }

        return updated.toResponse()
    }

    fun getSlaStatus(userId: Long, approvalId: Long): List<ApprovalChainSlaResponse> {
        val approval = approvalRepository.findById(approvalId) ?: throw NotFoundException("승인 요청", approvalId)
        if (approval.userId != userId && approval.reviewerId != userId) {
            throw ForbiddenException("해당 승인 요청에 대한 접근 권한이 없습니다")
        }
        val steps = approvalChainRepository.findByApprovalId(approvalId)
        val now = LocalDateTime.now()

        return steps.map { step ->
            val remainingMinutes = if (step.deadlineAt != null && step.status == "PENDING") {
                java.time.Duration.between(now, step.deadlineAt).toMinutes()
            } else null

            ApprovalChainSlaResponse(
                stepId = step.id!!,
                stepOrder = step.stepOrder,
                approverName = step.approverName,
                status = step.status,
                deadlineAt = step.deadlineAt,
                remainingMinutes = remainingMinutes,
                isOverdue = remainingMinutes != null && remainingMinutes < 0,
            )
        }
    }

    private fun ApprovalChain.toResponse() = ApprovalChainResponse(
        id = id!!,
        approvalId = approvalId,
        stepOrder = stepOrder,
        approverId = approverId,
        approverName = approverName,
        status = status,
        deadlineAt = deadlineAt,
        approvedAt = approvedAt,
        comment = comment,
        createdAt = createdAt,
    )
}
