package com.ongo.domain.contentcalendarai

interface CalendarSuggestionRepository {
    fun findByWorkspaceId(workspaceId: Long): List<CalendarSuggestion>
    fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<CalendarSuggestion>
    fun findById(id: Long): CalendarSuggestion?
    fun save(entity: CalendarSuggestion): CalendarSuggestion
    fun saveBatch(entities: List<CalendarSuggestion>): List<CalendarSuggestion>
    fun updateStatus(id: Long, status: String)
}
