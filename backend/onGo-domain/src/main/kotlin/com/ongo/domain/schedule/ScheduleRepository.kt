package com.ongo.domain.schedule

import java.time.LocalDateTime

interface ScheduleRepository {
    fun findById(id: Long): Schedule?
    fun findByUserId(userId: Long): List<Schedule>
    fun findByUserIdAndDateRange(userId: Long, from: LocalDateTime, to: LocalDateTime): List<Schedule>
    fun findDueSchedules(now: LocalDateTime): List<Schedule>
    /**
     * Atomically claims a due schedule for processing.
     *
     * The caller must run the returned work in its own transaction. A null result
     * means another worker claimed it first (or it is no longer due).
     */
    fun claimDue(id: Long, now: LocalDateTime): Schedule?
    fun save(schedule: Schedule): Schedule
    fun update(schedule: Schedule): Schedule
    fun delete(id: Long)
}
