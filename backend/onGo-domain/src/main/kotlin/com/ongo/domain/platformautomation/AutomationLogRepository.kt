package com.ongo.domain.platformautomation

interface AutomationLogRepository {
    fun findByRuleId(ruleId: Long): List<AutomationLog>
    fun findByUserId(userId: Long): List<AutomationLog>
    fun save(log: AutomationLog): AutomationLog
}
