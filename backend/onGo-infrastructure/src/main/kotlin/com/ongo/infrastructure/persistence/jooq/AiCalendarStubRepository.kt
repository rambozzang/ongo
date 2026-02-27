package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.aicalendar.*
import org.springframework.stereotype.Repository

@Repository
class AiCalendarStubRepository : AiCalendarRepository {
    override fun findById(id: Long): AiContentCalendar? = null
    override fun findByUserId(userId: Long): List<AiContentCalendar> = emptyList()
    override fun save(calendar: AiContentCalendar): AiContentCalendar = calendar.copy(id = 1)
    override fun update(calendar: AiContentCalendar): AiContentCalendar = calendar
    override fun delete(id: Long) {}
}
