package com.ongo.application.recurring

import com.ongo.application.recurring.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.recurring.RecurringSchedule
import com.ongo.domain.recurring.RecurringScheduleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@Service
class RecurringScheduleUseCase(
    private val recurringScheduleRepository: RecurringScheduleRepository,
) {

    companion object {
        val FREQUENCIES = setOf("DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY")
    }

    fun listSchedules(userId: Long): List<RecurringScheduleResponse> {
        return recurringScheduleRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createSchedule(userId: Long, request: CreateRecurringScheduleRequest): RecurringScheduleResponse {
        require(request.frequency in FREQUENCIES) { "유효하지 않은 빈도: ${request.frequency}" }

        val timeOfDay = LocalTime.parse(request.timeOfDay)
        val schedule = RecurringSchedule(
            userId = userId,
            name = request.name,
            frequency = request.frequency,
            dayOfWeek = request.dayOfWeek,
            dayOfMonth = request.dayOfMonth,
            timeOfDay = timeOfDay,
            timezone = request.timezone,
            platforms = request.platforms,
            titleTemplate = request.titleTemplate,
            descriptionTemplate = request.descriptionTemplate,
            tags = request.tags,
            isActive = request.isActive,
            nextRunAt = calculateNextRunAt(request.frequency, request.dayOfWeek, request.dayOfMonth, timeOfDay, request.timezone),
        )
        return recurringScheduleRepository.save(schedule).toResponse()
    }

    @Transactional
    fun updateSchedule(userId: Long, scheduleId: Long, request: UpdateRecurringScheduleRequest): RecurringScheduleResponse {
        val schedule = recurringScheduleRepository.findById(scheduleId) ?: throw NotFoundException("반복 예약", scheduleId)
        if (schedule.userId != userId) throw ForbiddenException("해당 반복 예약에 대한 권한이 없습니다")

        request.frequency?.let { require(it in FREQUENCIES) { "유효하지 않은 빈도: $it" } }

        val newTimeOfDay = request.timeOfDay?.let { LocalTime.parse(it) } ?: schedule.timeOfDay
        val newFrequency = request.frequency ?: schedule.frequency
        val newDayOfWeek = request.dayOfWeek ?: schedule.dayOfWeek
        val newDayOfMonth = request.dayOfMonth ?: schedule.dayOfMonth
        val newTimezone = request.timezone ?: schedule.timezone

        val updated = schedule.copy(
            name = request.name ?: schedule.name,
            frequency = newFrequency,
            dayOfWeek = newDayOfWeek,
            dayOfMonth = newDayOfMonth,
            timeOfDay = newTimeOfDay,
            timezone = newTimezone,
            platforms = request.platforms ?: schedule.platforms,
            titleTemplate = request.titleTemplate ?: schedule.titleTemplate,
            descriptionTemplate = request.descriptionTemplate ?: schedule.descriptionTemplate,
            tags = request.tags ?: schedule.tags,
            nextRunAt = calculateNextRunAt(newFrequency, newDayOfWeek, newDayOfMonth, newTimeOfDay, newTimezone),
        )
        return recurringScheduleRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteSchedule(userId: Long, scheduleId: Long) {
        val schedule = recurringScheduleRepository.findById(scheduleId) ?: throw NotFoundException("반복 예약", scheduleId)
        if (schedule.userId != userId) throw ForbiddenException("해당 반복 예약에 대한 권한이 없습니다")
        recurringScheduleRepository.delete(scheduleId)
    }

    @Transactional
    fun toggleSchedule(userId: Long, scheduleId: Long): RecurringScheduleResponse {
        val schedule = recurringScheduleRepository.findById(scheduleId) ?: throw NotFoundException("반복 예약", scheduleId)
        if (schedule.userId != userId) throw ForbiddenException("해당 반복 예약에 대한 권한이 없습니다")

        val updated = schedule.copy(isActive = !schedule.isActive)
        return recurringScheduleRepository.update(updated).toResponse()
    }

    private fun calculateNextRunAt(
        frequency: String,
        dayOfWeek: Int?,
        dayOfMonth: Int?,
        timeOfDay: LocalTime,
        timezone: String,
    ): LocalDateTime {
        val zone = ZoneId.of(timezone)
        val now = LocalDateTime.now(zone)
        val todayAtTime = now.toLocalDate().atTime(timeOfDay)

        return when (frequency) {
            "DAILY" -> if (todayAtTime.isAfter(now)) todayAtTime else todayAtTime.plusDays(1)
            "WEEKLY" -> {
                val target = dayOfWeek?.let { DayOfWeek.of(it) } ?: now.dayOfWeek
                val next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(target)).atTime(timeOfDay)
                if (next.isAfter(now)) next else next.plusWeeks(1)
            }
            "BIWEEKLY" -> {
                val target = dayOfWeek?.let { DayOfWeek.of(it) } ?: now.dayOfWeek
                val next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(target)).atTime(timeOfDay)
                if (next.isAfter(now)) next else next.plusWeeks(2)
            }
            "MONTHLY" -> {
                val day = dayOfMonth ?: 1
                val nextDate = if (now.dayOfMonth < day || (now.dayOfMonth == day && todayAtTime.isAfter(now))) {
                    now.toLocalDate().withDayOfMonth(day.coerceAtMost(now.toLocalDate().lengthOfMonth()))
                } else {
                    now.toLocalDate().plusMonths(1).withDayOfMonth(day.coerceAtMost(now.toLocalDate().plusMonths(1).lengthOfMonth()))
                }
                nextDate.atTime(timeOfDay)
            }
            else -> todayAtTime.plusDays(1)
        }
    }

    private fun RecurringSchedule.toResponse() = RecurringScheduleResponse(
        id = id!!,
        name = name,
        frequency = frequency,
        dayOfWeek = dayOfWeek,
        dayOfMonth = dayOfMonth,
        timeOfDay = timeOfDay,
        timezone = timezone,
        platforms = platforms,
        titleTemplate = titleTemplate,
        descriptionTemplate = descriptionTemplate,
        tags = tags,
        isActive = isActive,
        nextRunAt = nextRunAt,
        lastRunAt = lastRunAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
