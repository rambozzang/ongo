package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentcalendarai.*
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class CalendarAiSlotStubRepository : CalendarAiSlotRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<CalendarAiSlot> = emptyList()
    override fun findByWorkspaceIdAndDateBetween(workspaceId: Long, startDate: LocalDate, endDate: LocalDate): List<CalendarAiSlot> = emptyList()
}

@Repository
class CalendarSuggestionStubRepository : CalendarSuggestionRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<CalendarSuggestion> = emptyList()
    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<CalendarSuggestion> = emptyList()
    override fun findById(id: Long): CalendarSuggestion? = null
    override fun save(entity: CalendarSuggestion): CalendarSuggestion = entity.copy(id = 1)
    override fun saveBatch(entities: List<CalendarSuggestion>): List<CalendarSuggestion> = entities
    override fun updateStatus(id: Long, status: String) {}
}
