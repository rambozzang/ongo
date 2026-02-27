package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.smartreply.*
import org.springframework.stereotype.Repository

@Repository
class SmartReplyRuleStubRepository : SmartReplyRuleRepository {
    override fun findById(id: Long): SmartReplyRule? = null
    override fun findByUserId(userId: Long): List<SmartReplyRule> = emptyList()
    override fun save(rule: SmartReplyRule): SmartReplyRule = rule.copy(id = 1)
    override fun update(rule: SmartReplyRule): SmartReplyRule = rule
    override fun delete(id: Long) {}
}

@Repository
class SmartReplySuggestionStubRepository : SmartReplySuggestionRepository {
    override fun findById(id: String): SmartReplySuggestion? = null
    override fun findByUserId(userId: Long): List<SmartReplySuggestion> = emptyList()
    override fun save(suggestion: SmartReplySuggestion): SmartReplySuggestion = suggestion
    override fun delete(id: String) {}
}

@Repository
class SmartReplyConfigStubRepository : SmartReplyConfigRepository {
    override fun findByUserId(userId: Long): SmartReplyConfig? = null
    override fun save(config: SmartReplyConfig): SmartReplyConfig = config.copy(id = 1)
    override fun update(config: SmartReplyConfig): SmartReplyConfig = config
}
