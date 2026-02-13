package com.ongo.application.approval

import com.ongo.domain.approval.ApprovalChainRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ApprovalSlaScheduler(
    private val approvalChainRepository: ApprovalChainRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {

    private val log = LoggerFactory.getLogger(ApprovalSlaScheduler::class.java)

    /**
     * 매 15분마다 SLA 마감 임박 및 초과 승인을 확인합니다.
     */
    @Scheduled(fixedRate = 900_000) // 15 minutes
    fun checkApprovalSla() {
        // 마감 60분 이내 승인 알림
        val approaching = approvalChainRepository.findApproachingDeadlineSteps(60)
        for (step in approaching) {
            log.info("승인 SLA 마감 임박: approvalId={}, stepOrder={}, approver={}",
                step.approvalId, step.stepOrder, step.approverName)
            eventPublisher.publishEvent(ApprovalSlaWarningEvent(
                approvalId = step.approvalId,
                stepId = step.id!!,
                approverId = step.approverId,
                approverName = step.approverName,
                deadlineAt = step.deadlineAt!!,
            ))
        }

        // 마감 초과 승인 알림
        val overdue = approvalChainRepository.findOverdueSteps()
        for (step in overdue) {
            log.warn("승인 SLA 초과: approvalId={}, stepOrder={}, approver={}",
                step.approvalId, step.stepOrder, step.approverName)
            eventPublisher.publishEvent(ApprovalSlaOverdueEvent(
                approvalId = step.approvalId,
                stepId = step.id!!,
                approverId = step.approverId,
                approverName = step.approverName,
                deadlineAt = step.deadlineAt!!,
            ))
        }
    }
}

data class ApprovalSlaWarningEvent(
    val approvalId: Long,
    val stepId: Long,
    val approverId: Long,
    val approverName: String,
    val deadlineAt: java.time.LocalDateTime,
)

data class ApprovalSlaOverdueEvent(
    val approvalId: Long,
    val stepId: Long,
    val approverId: Long,
    val approverName: String,
    val deadlineAt: java.time.LocalDateTime,
)
