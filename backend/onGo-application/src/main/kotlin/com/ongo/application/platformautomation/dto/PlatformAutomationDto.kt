package com.ongo.application.platformautomation.dto

import java.time.LocalDateTime

data class AutomationRuleResponse(
    val id: Long,
    val name: String,
    val platform: String,
    val triggerType: String,
    val actionType: String,
    val condition: String,
    val isActive: Boolean,
    val executionCount: Int,
    val lastExecuted: LocalDateTime?,
    val createdAt: LocalDateTime?,
)

data class AutomationLogResponse(
    val id: Long,
    val ruleId: Long,
    val ruleName: String,
    val status: String,
    val message: String?,
    val executedAt: LocalDateTime?,
)

data class PlatformAutomationSummaryResponse(
    val totalRules: Int,
    val activeRules: Int,
    val totalExecutions: Int,
    val successRate: Double,
    val topPlatform: String,
)

data class CreateRuleRequest(
    val name: String,
    val platform: String,
    val triggerType: String,
    val actionType: String,
    val condition: String,
)
