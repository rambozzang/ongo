package com.ongo.application.recurring

import com.ongo.application.recurring.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.recurring.RecurringSchedule
import com.ongo.domain.recurring.RecurringScheduleRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.channel.ChannelRepository
import com.ongo.common.enums.Platform
import com.ongo.application.video.StorageService
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
    private val videoRepository: VideoRepository,
    private val userWriteGuard: UserWriteGuard,
    private val channelRepository: ChannelRepository,
    private val storageService: StorageService,
) {

    companion object {
        val FREQUENCIES = setOf("DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY", "INTERVAL")
        val STORAGE_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }

    fun listSchedules(userId: Long): List<RecurringScheduleResponse> {
        return recurringScheduleRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createSchedule(userId: Long, request: CreateRecurringScheduleRequest): RecurringScheduleResponse {
        userWriteGuard.requireWritable(userId)
        require(request.frequency in FREQUENCIES) { "유효하지 않은 빈도: ${request.frequency}" }
        require(request.name.isNotBlank()) { "반복 예약 이름을 입력하세요." }
        validateCadence(request.frequency, request.intervalDays, request.dayOfWeek, request.dayOfMonth, request.timezone)
        validateSource(userId, request.videoId, request.platforms)

        val timeOfDay = LocalTime.parse(request.timeOfDay)
        val schedule = RecurringSchedule(
            userId = userId,
            videoId = request.videoId,
            name = request.name,
            frequency = request.frequency,
            intervalDays = request.intervalDays,
            dayOfWeek = request.dayOfWeek,
            dayOfMonth = request.dayOfMonth,
            timeOfDay = timeOfDay,
            timezone = request.timezone,
            platforms = request.platforms,
            titleTemplate = request.titleTemplate,
            descriptionTemplate = request.descriptionTemplate,
            tags = request.tags,
            isActive = request.isActive,
            nextRunAt = calculateNextRunAt(request.frequency, request.intervalDays, request.dayOfWeek, request.dayOfMonth, timeOfDay, request.timezone),
        )
        return recurringScheduleRepository.save(schedule).toResponse()
    }

    @Transactional
    fun updateSchedule(userId: Long, scheduleId: Long, request: UpdateRecurringScheduleRequest): RecurringScheduleResponse {
        userWriteGuard.requireWritable(userId)
        val schedule = recurringScheduleRepository.findById(scheduleId) ?: throw NotFoundException("반복 예약", scheduleId)
        if (schedule.userId != userId) throw ForbiddenException("해당 반복 예약에 대한 권한이 없습니다")

        request.frequency?.let { require(it in FREQUENCIES) { "유효하지 않은 빈도: $it" } }
        val nextVideoId = request.videoId ?: schedule.videoId
            ?: throw IllegalStateException("반복 게시 원본 영상이 없습니다.")
        validateSource(userId, nextVideoId, request.platforms ?: schedule.platforms)

        val newTimeOfDay = request.timeOfDay?.let { LocalTime.parse(it) } ?: schedule.timeOfDay
        val newFrequency = request.frequency ?: schedule.frequency
        val newIntervalDays = when {
            request.frequency != null && request.frequency != schedule.frequency -> request.intervalDays
            else -> request.intervalDays ?: schedule.intervalDays
        }
        val newDayOfWeek = request.dayOfWeek ?: schedule.dayOfWeek
        val newDayOfMonth = request.dayOfMonth ?: schedule.dayOfMonth
        val newTimezone = request.timezone ?: schedule.timezone
        require((request.name ?: schedule.name).isNotBlank()) { "반복 예약 이름을 입력하세요." }
        validateCadence(newFrequency, newIntervalDays, newDayOfWeek, newDayOfMonth, newTimezone)

        val updated = schedule.copy(
            videoId = request.videoId ?: schedule.videoId,
            name = request.name ?: schedule.name,
            frequency = newFrequency,
            intervalDays = newIntervalDays,
            dayOfWeek = newDayOfWeek,
            dayOfMonth = newDayOfMonth,
            timeOfDay = newTimeOfDay,
            timezone = newTimezone,
            platforms = request.platforms ?: schedule.platforms,
            titleTemplate = request.titleTemplate ?: schedule.titleTemplate,
            descriptionTemplate = request.descriptionTemplate ?: schedule.descriptionTemplate,
            tags = request.tags ?: schedule.tags,
            nextRunAt = calculateNextRunAt(newFrequency, newIntervalDays, newDayOfWeek, newDayOfMonth, newTimeOfDay, newTimezone),
        )
        return recurringScheduleRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteSchedule(userId: Long, scheduleId: Long) {
        userWriteGuard.requireWritable(userId)
        val schedule = recurringScheduleRepository.findById(scheduleId) ?: throw NotFoundException("반복 예약", scheduleId)
        if (schedule.userId != userId) throw ForbiddenException("해당 반복 예약에 대한 권한이 없습니다")
        recurringScheduleRepository.delete(scheduleId)
    }

    @Transactional
    fun toggleSchedule(userId: Long, scheduleId: Long): RecurringScheduleResponse {
        userWriteGuard.requireWritable(userId)
        val schedule = recurringScheduleRepository.findById(scheduleId) ?: throw NotFoundException("반복 예약", scheduleId)
        if (schedule.userId != userId) throw ForbiddenException("해당 반복 예약에 대한 권한이 없습니다")

        val updated = schedule.copy(isActive = !schedule.isActive)
        return recurringScheduleRepository.update(updated).toResponse()
    }

    private fun calculateNextRunAt(
        frequency: String,
        intervalDays: Int?,
        dayOfWeek: Int?,
        dayOfMonth: Int?,
        timeOfDay: LocalTime,
        timezone: String,
        reference: LocalDateTime = LocalDateTime.now(ZoneId.of(timezone)),
    ): LocalDateTime {
        val zone = ZoneId.of(timezone)
        val now = reference
        val todayAtTime = now.toLocalDate().atTime(timeOfDay)

        val nextLocal = when (frequency) {
            "DAILY" -> if (todayAtTime.isAfter(now)) todayAtTime else todayAtTime.plusDays(1)
            "INTERVAL" -> todayAtTime.plusDays(requireNotNull(intervalDays).toLong())
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
        // The database due query uses one shared wall-clock zone. Persist the
        // instant in KST while calculating the recurrence in the user's zone.
        return nextLocal.atZone(zone).withZoneSameInstant(STORAGE_ZONE).toLocalDateTime()
    }

    /** Calculates the next occurrence strictly after a consumed occurrence. */
    fun nextRunAtAfter(schedule: RecurringSchedule, occurrence: LocalDateTime): LocalDateTime =
        ZoneId.of(schedule.timezone).let { zone ->
            calculateNextRunAt(
                schedule.frequency,
                schedule.intervalDays,
                schedule.dayOfWeek,
                schedule.dayOfMonth,
                schedule.timeOfDay,
                schedule.timezone,
                occurrence.atZone(STORAGE_ZONE).withZoneSameInstant(zone).toLocalDateTime(),
            )
        }

    private fun validateSource(userId: Long, videoId: Long, platforms: List<String>) {
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 권한이 없습니다")
        require(!video.fileUrl.isNullOrBlank()) { "반복 게시할 원본 영상 파일이 없습니다." }
        // A database URL alone is not durable: it may be an expired presigned
        // URL or an external URL that onGo cannot refresh for the next run.
        // Verify that the source object is owned by our durable storage now.
        require(runCatching { storageService.getFileUrl(videoId, video.fileUrl) }.isSuccess) {
            "반복 게시할 원본 영상이 onGo 스토리지에 없습니다. 영상을 다시 업로드해주세요."
        }
        validatePlatforms(userId, platforms)
    }

    private fun validatePlatforms(userId: Long, platforms: List<String>) {
        require(platforms.isNotEmpty()) { "반복 게시 플랫폼을 하나 이상 선택하세요." }
        require(platforms.map { it.uppercase() }.distinct().size == platforms.size) {
            "반복 게시 플랫폼은 중복될 수 없습니다."
        }
        platforms.forEach { value ->
            val platformName = value.substringBefore('#').uppercase()
            val channelId = value.substringAfter('#', "").takeIf { it.isNotBlank() }?.toLongOrNull()
            require('#' !in value || channelId != null && channelId > 0) {
                "유효하지 않은 반복 게시 계정입니다: $value"
            }
            require(runCatching { Platform.valueOf(platformName) }.isSuccess) {
                "지원하지 않는 반복 게시 플랫폼입니다: $value"
            }
            if (channelId != null) {
                val channel = channelRepository.findById(channelId)
                require(channel?.userId == userId && channel.platform.name == platformName) {
                    "반복 게시 계정이 현재 사용자 또는 플랫폼과 일치하지 않습니다: $value"
                }
            }
        }
    }

    private fun validateCadence(frequency: String, intervalDays: Int?, dayOfWeek: Int?, dayOfMonth: Int?, timezone: String) {
        require(timezone.isNotBlank() && runCatching { ZoneId.of(timezone) }.isSuccess) {
            "유효하지 않은 시간대입니다: $timezone"
        }
        if (frequency == "WEEKLY" || frequency == "BIWEEKLY") {
            require(dayOfWeek == null || dayOfWeek in 1..7) { "요일은 1(월요일)부터 7(일요일)까지 입력하세요." }
        }
        if (frequency == "MONTHLY") {
            require(dayOfMonth == null || dayOfMonth in 1..31) { "월간 게시일은 1일부터 31일까지 입력하세요." }
        }
        if (frequency == "INTERVAL") {
            require(intervalDays != null && intervalDays in 1..365) { "반복 간격은 1~365일 사이여야 합니다." }
        } else {
            require(intervalDays == null) { "intervalDays는 INTERVAL 빈도에서만 사용할 수 있습니다." }
        }
    }

    private fun RecurringSchedule.toResponse() = RecurringScheduleResponse(
        id = id!!,
        name = name,
        frequency = frequency,
        intervalDays = intervalDays,
        dayOfWeek = dayOfWeek,
        dayOfMonth = dayOfMonth,
        timeOfDay = timeOfDay,
        timezone = timezone,
        platforms = platforms,
        titleTemplate = titleTemplate,
        descriptionTemplate = descriptionTemplate,
        tags = tags,
        videoId = videoId,
        isActive = isActive,
        nextRunAt = nextRunAt,
        lastRunAt = lastRunAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
