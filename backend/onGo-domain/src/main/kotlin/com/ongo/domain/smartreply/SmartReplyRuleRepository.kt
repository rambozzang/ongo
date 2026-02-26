package com.ongo.domain.smartreply

interface SmartReplyRuleRepository {
    fun findById(id: Long): SmartReplyRule?
    fun findByUserId(userId: Long): List<SmartReplyRule>
    fun save(rule: SmartReplyRule): SmartReplyRule
    fun update(rule: SmartReplyRule): SmartReplyRule
    fun delete(id: Long)
}
