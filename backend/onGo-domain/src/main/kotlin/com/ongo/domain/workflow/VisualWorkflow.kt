package com.ongo.domain.workflow

import java.time.LocalDateTime

data class VisualWorkflow(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val description: String? = null,
    val workflowData: String, // JSONB as String
    val isActive: Boolean = false,
    val lastTriggeredAt: LocalDateTime? = null,
    val triggerCount: Int = 0,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
