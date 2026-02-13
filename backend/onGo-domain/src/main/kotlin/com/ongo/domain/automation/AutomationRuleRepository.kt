package com.ongo.domain.automation

interface AutomationRuleRepository {
    fun findById(id: Long): AutomationRule?
    fun findByUserId(userId: Long): List<AutomationRule>
    fun findAll(): List<AutomationRule>
    fun save(rule: AutomationRule): AutomationRule
    fun update(rule: AutomationRule): AutomationRule
    fun delete(id: Long)
}
