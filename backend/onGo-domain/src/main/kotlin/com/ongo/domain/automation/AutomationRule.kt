package com.ongo.domain.automation

import java.time.LocalDateTime

data class AutomationRule(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val description: String? = null,
    val triggerType: String,
    val triggerConfig: Map<String, Any?> = emptyMap(),
    val actionType: String,
    val actionConfig: Map<String, Any?> = emptyMap(),
    val isActive: Boolean = false,
    val lastTriggeredAt: LocalDateTime? = null,
    val executionCount: Int = 0,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
