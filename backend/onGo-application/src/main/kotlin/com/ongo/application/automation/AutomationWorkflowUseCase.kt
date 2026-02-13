package com.ongo.application.automation

import com.ongo.application.automation.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.automation.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class AutomationWorkflowUseCase(
    private val workflowRepository: AutomationWorkflowRepository,
    private val conditionEvaluator: WorkflowConditionEvaluator,
    private val workflowExecutor: WorkflowExecutor,
) {

    private val log = LoggerFactory.getLogger(AutomationWorkflowUseCase::class.java)

    companion object {
        val WORKFLOW_TRIGGER_TYPES = setOf(
            "VIDEO_UPLOADED", "SCHEDULE_DUE", "COMMENT_RECEIVED",
            "ANALYTICS_MILESTONE", "CREDIT_LOW", "VIDEO_PUBLISHED",
            "SUBSCRIBER_MILESTONE"
        )
        val WORKFLOW_ACTION_TYPES = setOf(
            "SEND_NOTIFICATION", "AUTO_PUBLISH", "ADD_TAG",
            "GENERATE_METADATA", "CROSS_POST", "MOVE_STATUS"
        )
    }

    fun listWorkflows(userId: Long): List<WorkflowResponse> {
        return workflowRepository.findByUserId(userId).map { it.toResponse() }
    }

    fun getWorkflow(userId: Long, workflowId: Long): WorkflowResponse {
        val workflow = workflowRepository.findById(workflowId)
            ?: throw NotFoundException("워크플로우", workflowId)
        if (workflow.userId != userId) throw ForbiddenException("해당 워크플로우에 대한 권한이 없습니다")
        return workflow.toResponse()
    }

    @Transactional
    fun createWorkflow(userId: Long, request: CreateWorkflowRequest): WorkflowResponse {
        require(request.triggerType in WORKFLOW_TRIGGER_TYPES) {
            "유효하지 않은 트리거 타입: ${request.triggerType}"
        }
        request.actions.forEach { action ->
            require(action.actionType in WORKFLOW_ACTION_TYPES) {
                "유효하지 않은 액션 타입: ${action.actionType}"
            }
        }

        val workflow = AutomationWorkflow(
            userId = userId,
            name = request.name,
            description = request.description,
            triggerType = request.triggerType,
            triggerConfig = request.triggerConfig,
            conditions = request.conditions.mapIndexed { i, c -> c.toDomain(i) },
            actions = request.actions.mapIndexed { i, a -> a.toDomain(i) },
            enabled = request.enabled,
        )

        return workflowRepository.save(workflow).toResponse()
    }

    @Transactional
    fun updateWorkflow(userId: Long, workflowId: Long, request: UpdateWorkflowRequest): WorkflowResponse {
        val existing = workflowRepository.findById(workflowId)
            ?: throw NotFoundException("워크플로우", workflowId)
        if (existing.userId != userId) throw ForbiddenException("해당 워크플로우에 대한 권한이 없습니다")

        request.triggerType?.let {
            require(it in WORKFLOW_TRIGGER_TYPES) { "유효하지 않은 트리거 타입: $it" }
        }
        request.actions?.forEach { action ->
            require(action.actionType in WORKFLOW_ACTION_TYPES) { "유효하지 않은 액션 타입: ${action.actionType}" }
        }

        val updated = existing.copy(
            name = request.name ?: existing.name,
            description = request.description ?: existing.description,
            triggerType = request.triggerType ?: existing.triggerType,
            triggerConfig = request.triggerConfig ?: existing.triggerConfig,
            conditions = request.conditions?.mapIndexed { i, c -> c.toDomain(i) } ?: existing.conditions,
            actions = request.actions?.mapIndexed { i, a -> a.toDomain(i) } ?: existing.actions,
        )

        return workflowRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteWorkflow(userId: Long, workflowId: Long) {
        val workflow = workflowRepository.findById(workflowId)
            ?: throw NotFoundException("워크플로우", workflowId)
        if (workflow.userId != userId) throw ForbiddenException("해당 워크플로우에 대한 권한이 없습니다")
        workflowRepository.delete(workflowId)
    }

    @Transactional
    fun toggleWorkflow(userId: Long, workflowId: Long): WorkflowResponse {
        val workflow = workflowRepository.findById(workflowId)
            ?: throw NotFoundException("워크플로우", workflowId)
        if (workflow.userId != userId) throw ForbiddenException("해당 워크플로우에 대한 권한이 없습니다")

        val updated = workflow.copy(enabled = !workflow.enabled)
        return workflowRepository.update(updated).toResponse()
    }

    fun getExecutionHistory(userId: Long, workflowId: Long): List<WorkflowExecutionResponse> {
        val workflow = workflowRepository.findById(workflowId)
            ?: throw NotFoundException("워크플로우", workflowId)
        if (workflow.userId != userId) throw ForbiddenException("해당 워크플로우에 대한 권한이 없습니다")

        return workflowRepository.findExecutionsByWorkflowId(workflowId).map { it.toResponse() }
    }

    /**
     * Execute a workflow when its trigger fires.
     * Called by event handlers or schedulers.
     */
    @Transactional
    fun executeWorkflow(workflowId: Long, triggerData: Map<String, Any?>) {
        val workflow = workflowRepository.findById(workflowId) ?: return
        if (!workflow.enabled) return

        log.info("Executing workflow '{}' (id={})", workflow.name, workflow.id)

        val execution = WorkflowExecution(
            workflowId = workflow.id!!,
            userId = workflow.userId,
            triggerData = triggerData,
        )

        try {
            // Evaluate conditions
            val conditionsMet = conditionEvaluator.evaluate(workflow.conditions, triggerData)

            if (!conditionsMet) {
                log.info("Workflow '{}' conditions not met, skipping", workflow.name)
                val skipped = execution.copy(
                    status = WorkflowExecutionStatus.COMPLETED,
                    actionResults = listOf(
                        ActionExecutionResult(
                            actionType = "CONDITION_CHECK",
                            sortOrder = 0,
                            status = "SKIPPED",
                            message = "조건이 충족되지 않아 건너뜀",
                        )
                    ),
                    completedAt = LocalDateTime.now(),
                )
                workflowRepository.saveExecution(skipped)
                return
            }

            // Execute actions
            val results = workflowExecutor.executeActions(workflow.actions, triggerData)

            val hasFailures = results.any { it.status == "FAILED" }
            val finalStatus = if (hasFailures) WorkflowExecutionStatus.FAILED else WorkflowExecutionStatus.COMPLETED

            val completed = execution.copy(
                status = finalStatus,
                actionResults = results,
                completedAt = LocalDateTime.now(),
            )
            workflowRepository.saveExecution(completed)

            // Update execution count
            workflowRepository.update(
                workflow.copy(
                    executionCount = workflow.executionCount + 1,
                    lastExecutedAt = LocalDateTime.now(),
                )
            )
        } catch (e: Exception) {
            log.error("Workflow execution failed for workflow '{}'", workflow.name, e)
            val failed = execution.copy(
                status = WorkflowExecutionStatus.FAILED,
                errorMessage = e.message,
                completedAt = LocalDateTime.now(),
            )
            workflowRepository.saveExecution(failed)
        }
    }

    // ─── DTO mapping ───

    private fun ConditionRequest.toDomain(sortOrder: Int): WorkflowCondition {
        return WorkflowCondition(
            groupType = groupType?.let { ConditionGroupType.valueOf(it) } ?: ConditionGroupType.AND,
            field = field,
            operator = operator?.let { ConditionOperator.valueOf(it) },
            value = value,
            expression = expression,
            nestedConditions = nestedConditions?.mapIndexed { i, c -> c.toDomain(i) } ?: emptyList(),
        )
    }

    private fun ActionRequest.toDomain(sortOrder: Int): WorkflowAction {
        return WorkflowAction(
            actionType = actionType,
            config = config,
            delayMinutes = delayMinutes ?: 0,
            sortOrder = sortOrder,
        )
    }

    private fun AutomationWorkflow.toResponse() = WorkflowResponse(
        id = id!!,
        name = name,
        description = description,
        triggerType = triggerType,
        triggerConfig = triggerConfig,
        conditions = conditions.map { it.toResponse() },
        actions = actions.map { it.toResponse() },
        enabled = enabled,
        executionCount = executionCount,
        lastExecutedAt = lastExecutedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun WorkflowCondition.toResponse(): ConditionResponse = ConditionResponse(
        id = id,
        groupType = groupType.name,
        field = field,
        operator = operator?.name,
        value = value,
        expression = expression,
        nestedConditions = nestedConditions.map { it.toResponse() },
    )

    private fun WorkflowAction.toResponse() = ActionResponse(
        id = id,
        actionType = actionType,
        config = config,
        delayMinutes = delayMinutes,
        sortOrder = sortOrder,
    )

    private fun WorkflowExecution.toResponse() = WorkflowExecutionResponse(
        id = id!!,
        workflowId = workflowId,
        triggerData = triggerData,
        status = status.name,
        actionResults = actionResults.map {
            ActionResultResponse(
                actionType = it.actionType,
                sortOrder = it.sortOrder,
                status = it.status,
                message = it.message,
                executedAt = it.executedAt,
            )
        },
        errorMessage = errorMessage,
        startedAt = startedAt,
        completedAt = completedAt,
    )
}
