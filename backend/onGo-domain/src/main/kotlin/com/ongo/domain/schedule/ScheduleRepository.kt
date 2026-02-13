package com.ongo.domain.schedule

import java.time.LocalDateTime

interface ScheduleRepository {
    fun findById(id: Long): Schedule?
    fun findByUserId(userId: Long): List<Schedule>
    fun findByUserIdAndDateRange(userId: Long, from: LocalDateTime, to: LocalDateTime): List<Schedule>
    fun findDueSchedules(now: LocalDateTime): List<Schedule>
    fun save(schedule: Schedule): Schedule
    fun update(schedule: Schedule): Schedule
    fun delete(id: Long)
}
