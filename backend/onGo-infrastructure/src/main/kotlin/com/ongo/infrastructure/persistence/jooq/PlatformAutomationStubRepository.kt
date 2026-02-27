package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.platformautomation.AutomationLog
import com.ongo.domain.platformautomation.AutomationLogRepository
import com.ongo.domain.platformautomation.AutomationRule
import com.ongo.domain.platformautomation.AutomationRuleRepository as PlatformAutomationRuleRepository
import org.springframework.stereotype.Repository

@Repository
class PlatformAutomationRuleStubRepository : PlatformAutomationRuleRepository {
    override fun findById(id: Long): AutomationRule? = null
    override fun findByUserId(userId: Long): List<AutomationRule> = emptyList()
    override fun save(rule: AutomationRule): AutomationRule = rule.copy(id = 1)
    override fun update(rule: AutomationRule): AutomationRule = rule
    override fun deleteById(id: Long) {}
}

@Repository
class AutomationLogStubRepository : AutomationLogRepository {
    override fun findByRuleId(ruleId: Long): List<AutomationLog> = emptyList()
    override fun findByUserId(userId: Long): List<AutomationLog> = emptyList()
    override fun save(log: AutomationLog): AutomationLog = log.copy(id = 1)
}
