package com.ongo.domain.calendarinsights

import java.time.LocalDate

interface CalendarInsightRepository {
    fun findByWorkspaceIdAndDateBetween(workspaceId: Long, startDate: LocalDate, endDate: LocalDate): List<CalendarInsight>
    fun findById(id: Long): CalendarInsight?
    fun save(insight: CalendarInsight): CalendarInsight
}
