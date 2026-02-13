package com.ongo.application.automation.dto

import java.time.LocalDateTime

// ─── Request DTOs ───

data class CreateWorkflowRequest(
    val name: String,
    val description: String? = null,
    val triggerType: String,
    val triggerConfig: Map<String, Any?> = emptyMap(),
    val conditions: List<ConditionRequest> = emptyList(),
    val actions: List<ActionRequest> = emptyList(),
    val enabled: Boolean = false,
)

data class UpdateWorkflowRequest(
    val name: String? = null,
    val description: String? = null,
    val triggerType: String? = null,
    val triggerConfig: Map<String, Any?>? = null,
    val conditions: List<ConditionRequest>? = null,
    val actions: List<ActionRequest>? = null,
)

data class ConditionRequest(
    val groupType: String? = "AND",
    val field: String? = null,
    val operator: String? = null,
    val value: String? = null,
    val expression: String? = null,
    val nestedConditions: List<ConditionRequest>? = null,
)

data class ActionRequest(
    val actionType: String,
    val config: Map<String, Any?> = emptyMap(),
    val delayMinutes: Int? = 0,
)

// ─── Response DTOs ───

data class WorkflowResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val triggerType: String,
    val triggerConfig: Map<String, Any?>,
    val conditions: List<ConditionResponse>,
    val actions: List<ActionResponse>,
    val enabled: Boolean,
    val executionCount: Int,
    val lastExecutedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class ConditionResponse(
    val id: Long?,
    val groupType: String,
    val field: String?,
    val operator: String?,
    val value: String?,
    val expression: String?,
    val nestedConditions: List<ConditionResponse>,
)

data class ActionResponse(
    val id: Long?,
    val actionType: String,
    val config: Map<String, Any?>,
    val delayMinutes: Int,
    val sortOrder: Int,
)

data class WorkflowExecutionResponse(
    val id: Long,
    val workflowId: Long,
    val triggerData: Map<String, Any?>,
    val status: String,
    val actionResults: List<ActionResultResponse>,
    val errorMessage: String?,
    val startedAt: LocalDateTime,
    val completedAt: LocalDateTime?,
)

data class ActionResultResponse(
    val actionType: String,
    val sortOrder: Int,
    val status: String,
    val message: String?,
    val executedAt: LocalDateTime,
)
