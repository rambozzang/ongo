package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.calendarinsights.*
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class CalendarInsightStubRepository : CalendarInsightRepository {
    override fun findByWorkspaceIdAndDateBetween(workspaceId: Long, startDate: LocalDate, endDate: LocalDate): List<CalendarInsight> = emptyList()
    override fun findById(id: Long): CalendarInsight? = null
    override fun save(insight: CalendarInsight): CalendarInsight = insight.copy(id = 1)
}

@Repository
class OptimalTimeSlotStubRepository : OptimalTimeSlotRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<OptimalTimeSlot> = emptyList()
    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<OptimalTimeSlot> = emptyList()
    override fun save(slot: OptimalTimeSlot): OptimalTimeSlot = slot.copy(id = 1)
}

@Repository
class UploadPatternStubRepository : UploadPatternRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<UploadPattern> = emptyList()
    override fun save(pattern: UploadPattern): UploadPattern = pattern.copy(id = 1)
}
