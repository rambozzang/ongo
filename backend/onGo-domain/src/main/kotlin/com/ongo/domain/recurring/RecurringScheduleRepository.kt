package com.ongo.domain.recurring

interface RecurringScheduleRepository {
    fun findById(id: Long): RecurringSchedule?
    fun findByUserId(userId: Long): List<RecurringSchedule>
    fun save(schedule: RecurringSchedule): RecurringSchedule
    fun update(schedule: RecurringSchedule): RecurringSchedule
    fun delete(id: Long)
}
