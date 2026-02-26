package com.ongo.domain.smartreply

interface SmartReplySuggestionRepository {
    fun findById(id: String): SmartReplySuggestion?
    fun findByUserId(userId: Long): List<SmartReplySuggestion>
    fun save(suggestion: SmartReplySuggestion): SmartReplySuggestion
    fun delete(id: String)
}
