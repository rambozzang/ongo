package com.ongo.domain.platformautomation

import java.time.LocalDateTime

data class AutomationLog(
    val id: Long? = null,
    val ruleId: Long,
    val ruleName: String,
    val status: String = "SUCCESS",
    val message: String? = null,
    val executedAt: LocalDateTime? = null,
)
