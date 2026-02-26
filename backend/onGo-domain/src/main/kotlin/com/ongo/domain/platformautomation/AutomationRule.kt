package com.ongo.domain.platformautomation

import java.time.LocalDateTime

data class AutomationRule(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val platform: String,
    val triggerType: String,
    val actionType: String,
    val conditionText: String,
    val isActive: Boolean = true,
    val executionCount: Int = 0,
    val lastExecuted: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
)
