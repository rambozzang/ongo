package com.ongo.domain.aicalendar

interface AiCalendarRepository {
    fun findById(id: Long): AiContentCalendar?
    fun findByUserId(userId: Long): List<AiContentCalendar>
    fun save(calendar: AiContentCalendar): AiContentCalendar
    fun update(calendar: AiContentCalendar): AiContentCalendar
    fun delete(id: Long)
}
