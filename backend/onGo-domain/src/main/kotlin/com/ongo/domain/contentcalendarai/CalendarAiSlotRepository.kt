package com.ongo.domain.contentcalendarai

import java.time.LocalDate

interface CalendarAiSlotRepository {
    fun findByWorkspaceId(workspaceId: Long): List<CalendarAiSlot>
    fun findByWorkspaceIdAndDateBetween(workspaceId: Long, startDate: LocalDate, endDate: LocalDate): List<CalendarAiSlot>
}
