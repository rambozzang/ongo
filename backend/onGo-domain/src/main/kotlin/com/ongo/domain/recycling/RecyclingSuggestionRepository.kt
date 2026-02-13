package com.ongo.domain.recycling

interface RecyclingSuggestionRepository {
    fun findByUserId(userId: Long, status: String? = null): List<RecyclingSuggestion>
    fun findById(id: Long): RecyclingSuggestion?
    fun save(suggestion: RecyclingSuggestion): RecyclingSuggestion
    fun saveAll(suggestions: List<RecyclingSuggestion>): List<RecyclingSuggestion>
    fun updateStatus(id: Long, status: String): RecyclingSuggestion?
    fun deleteByUserId(userId: Long)
}
