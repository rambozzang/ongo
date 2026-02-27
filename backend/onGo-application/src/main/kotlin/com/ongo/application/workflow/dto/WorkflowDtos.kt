package com.ongo.application.workflow.dto

import java.time.LocalDateTime

data class WorkflowResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val workflowData: String,
    val isActive: Boolean,
    val lastTriggeredAt: LocalDateTime?,
    val triggerCount: Int,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class CreateWorkflowRequest(
    val name: String,
    val description: String? = null,
    val workflowData: String,
)

data class UpdateWorkflowRequest(
    val name: String? = null,
    val description: String? = null,
    val workflowData: String? = null,
    val isActive: Boolean? = null,
)

data class WorkflowExecutionResponse(
    val id: Long,
    val workflowId: Long,
    val status: String,
    val startedAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
    val triggerCount: Int,
)

data class WorkflowTestResponse(
    val workflowId: Long,
    val status: String,
    val startedAt: LocalDateTime,
    val completedAt: LocalDateTime,
    val steps: List<String>,
)
