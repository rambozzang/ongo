package com.ongo.domain.platformautomation

interface AutomationRuleRepository {
    fun findById(id: Long): AutomationRule?
    fun findByUserId(userId: Long): List<AutomationRule>
    fun save(rule: AutomationRule): AutomationRule
    fun update(rule: AutomationRule): AutomationRule
    fun deleteById(id: Long)
}
