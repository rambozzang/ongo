package com.ongo.application.automation.dto

import java.time.LocalDateTime

data class AutomationRuleResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val triggerType: String,
    val triggerConfig: Map<String, Any?>,
    val actionType: String,
    val actionConfig: Map<String, Any?>,
    val isActive: Boolean,
    val lastTriggeredAt: LocalDateTime?,
    val executionCount: Int,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class CreateAutomationRuleRequest(
    val name: String,
    val description: String? = null,
    val triggerType: String,
    val triggerConfig: Map<String, Any?> = emptyMap(),
    val actionType: String,
    val actionConfig: Map<String, Any?> = emptyMap(),
    val isActive: Boolean = false,
)

data class UpdateAutomationRuleRequest(
    val name: String? = null,
    val description: String? = null,
    val triggerType: String? = null,
    val triggerConfig: Map<String, Any?>? = null,
    val actionType: String? = null,
    val actionConfig: Map<String, Any?>? = null,
)

data class SmartTriggerTemplate(
    val triggerType: String,
    val name: String,
    val description: String,
    val defaultConfig: Map<String, Any?>,
    val configSchema: Map<String, Any?>,
)
