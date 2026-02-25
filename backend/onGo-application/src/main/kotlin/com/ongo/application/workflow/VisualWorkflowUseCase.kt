package com.ongo.application.workflow

import com.ongo.application.workflow.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.workflow.VisualWorkflow
import com.ongo.domain.workflow.WorkflowRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class VisualWorkflowUseCase(
    private val workflowRepository: WorkflowRepository,
) {

    fun listWorkflows(userId: Long): List<WorkflowResponse> {
        return workflowRepository.findByUserId(userId).map { it.toResponse() }
    }

    fun getWorkflow(userId: Long, workflowId: Long): WorkflowResponse {
        val workflow = workflowRepository.findById(workflowId) ?: throw NotFoundException("워크플로우", workflowId)
        if (workflow.userId != userId) throw ForbiddenException("해당 워크플로우에 대한 권한이 없습니다")
        return workflow.toResponse()
    }

    @Transactional
    fun createWorkflow(userId: Long, request: CreateWorkflowRequest): WorkflowResponse {
        val workflow = VisualWorkflow(
            userId = userId,
            name = request.name,
            description = request.description,
            workflowData = request.workflowData,
        )
        return workflowRepository.save(workflow).toResponse()
    }

    @Transactional
    fun updateWorkflow(userId: Long, workflowId: Long, request: UpdateWorkflowRequest): WorkflowResponse {
        val workflow = workflowRepository.findById(workflowId) ?: throw NotFoundException("워크플로우", workflowId)
        if (workflow.userId != userId) throw ForbiddenException("해당 워크플로우에 대한 권한이 없습니다")
        val updated = workflow.copy(
            name = request.name ?: workflow.name,
            description = request.description ?: workflow.description,
            workflowData = request.workflowData ?: workflow.workflowData,
            isActive = request.isActive ?: workflow.isActive,
        )
        return workflowRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteWorkflow(userId: Long, workflowId: Long) {
        val workflow = workflowRepository.findById(workflowId) ?: throw NotFoundException("워크플로우", workflowId)
        if (workflow.userId != userId) throw ForbiddenException("해당 워크플로우에 대한 권한이 없습니다")
        workflowRepository.delete(workflowId)
    }

    private fun VisualWorkflow.toResponse() = WorkflowResponse(
        id = id!!,
        name = name,
        description = description,
        workflowData = workflowData,
        isActive = isActive,
        lastTriggeredAt = lastTriggeredAt,
        triggerCount = triggerCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
