package com.ongo.domain.recurring

import java.time.LocalDateTime

interface RecurringScheduleRepository {
    fun findById(id: Long): RecurringSchedule?
    fun findByUserId(userId: Long): List<RecurringSchedule>
    fun findDue(now: LocalDateTime): List<RecurringSchedule>
    fun markRun(id: Long, expectedNextRunAt: LocalDateTime, lastRunAt: LocalDateTime, nextRunAt: LocalDateTime): Boolean
    fun save(schedule: RecurringSchedule): RecurringSchedule
    fun update(schedule: RecurringSchedule): RecurringSchedule
    fun delete(id: Long)
}
