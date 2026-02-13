package com.ongo.domain.automation

import java.time.LocalDateTime

data class AutomationWorkflow(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val description: String? = null,
    val triggerType: String,
    val triggerConfig: Map<String, Any?> = emptyMap(),
    val conditions: List<WorkflowCondition> = emptyList(),
    val actions: List<WorkflowAction> = emptyList(),
    val enabled: Boolean = false,
    val executionCount: Int = 0,
    val lastExecutedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)

data class WorkflowCondition(
    val id: Long? = null,
    val workflowId: Long? = null,
    val groupType: ConditionGroupType = ConditionGroupType.AND,
    val field: String? = null,
    val operator: ConditionOperator? = null,
    val value: String? = null,
    val expression: String? = null,
    val parentConditionId: Long? = null,
    val nestedConditions: List<WorkflowCondition> = emptyList(),
    val sortOrder: Int = 0,
)

enum class ConditionGroupType {
    AND, OR
}

enum class ConditionOperator {
    EQUALS, NOT_EQUALS, GT, GTE, LT, LTE, CONTAINS, NOT_CONTAINS, REGEX, IN
}

data class WorkflowAction(
    val id: Long? = null,
    val workflowId: Long? = null,
    val actionType: String,
    val config: Map<String, Any?> = emptyMap(),
    val delayMinutes: Int = 0,
    val sortOrder: Int = 0,
)

data class WorkflowExecution(
    val id: Long? = null,
    val workflowId: Long,
    val userId: Long,
    val triggerData: Map<String, Any?> = emptyMap(),
    val status: WorkflowExecutionStatus = WorkflowExecutionStatus.RUNNING,
    val actionResults: List<ActionExecutionResult> = emptyList(),
    val errorMessage: String? = null,
    val startedAt: LocalDateTime = LocalDateTime.now(),
    val completedAt: LocalDateTime? = null,
)

enum class WorkflowExecutionStatus {
    RUNNING, COMPLETED, FAILED, CANCELLED
}

data class ActionExecutionResult(
    val actionType: String,
    val sortOrder: Int,
    val status: String,
    val message: String? = null,
    val executedAt: LocalDateTime = LocalDateTime.now(),
)
