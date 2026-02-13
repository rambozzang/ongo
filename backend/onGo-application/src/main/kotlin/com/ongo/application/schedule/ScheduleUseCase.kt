package com.ongo.application.schedule

import com.ongo.application.schedule.dto.*
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.common.util.safeValueOfOrThrow
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.VideoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class ScheduleUseCase(
    private val scheduleRepository: ScheduleRepository,
    private val videoRepository: VideoRepository,
    private val userRepository: UserRepository
) {
    companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }

    @Transactional
    fun createSchedule(userId: Long, request: CreateScheduleRequest): ScheduleResponse {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        val video = videoRepository.findById(request.videoId) ?: throw NotFoundException("영상", request.videoId)
        if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 권한이 없습니다")

        // 과거 시간 예약 방지
        val nowKst = LocalDateTime.now(KST)
        if (request.scheduledAt.isBefore(nowKst)) {
            throw IllegalArgumentException("예약 시간은 현재 시간 이후여야 합니다")
        }

        // 플랜별 예약 가능 기간 체크
        validateScheduleLimit(user.planType, request.scheduledAt)

        val platformConfigs = request.platforms.associate { config ->
            config.platform.name to mapOf("scheduledAt" to (config.scheduledAt ?: request.scheduledAt).toString())
        }

        val schedule = Schedule(
            videoId = request.videoId,
            userId = userId,
            scheduledAt = request.scheduledAt,
            status = ScheduleStatus.SCHEDULED,
            platforms = platformConfigs
        )

        val saved = scheduleRepository.save(schedule)
        return saved.toResponse(video.title, video.thumbnailUrls.firstOrNull())
    }

    fun getSchedules(userId: Long, from: LocalDateTime?, to: LocalDateTime?): ScheduleCalendarResponse {
        val effectiveFrom = from ?: LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0)
        val effectiveTo = to ?: effectiveFrom.plusMonths(1)

        val schedules = scheduleRepository.findByUserIdAndDateRange(userId, effectiveFrom, effectiveTo)
        return ScheduleCalendarResponse(
            schedules = schedules.map { schedule ->
                val video = videoRepository.findById(schedule.videoId)
                schedule.toResponse(video?.title, video?.thumbnailUrls?.firstOrNull())
            },
            from = effectiveFrom,
            to = effectiveTo
        )
    }

    fun getSchedule(userId: Long, scheduleId: Long): ScheduleResponse {
        val schedule = scheduleRepository.findById(scheduleId) ?: throw NotFoundException("예약", scheduleId)
        if (schedule.userId != userId) throw ForbiddenException("해당 예약에 대한 권한이 없습니다")
        val video = videoRepository.findById(schedule.videoId)
        return schedule.toResponse(video?.title, video?.thumbnailUrls?.firstOrNull())
    }

    @Transactional
    fun updateSchedule(userId: Long, scheduleId: Long, request: UpdateScheduleRequest): ScheduleResponse {
        val schedule = scheduleRepository.findById(scheduleId) ?: throw NotFoundException("예약", scheduleId)
        if (schedule.userId != userId) throw ForbiddenException("해당 예약에 대한 권한이 없습니다")
        if (schedule.status != ScheduleStatus.SCHEDULED) throw IllegalStateException("수정 가능한 상태가 아닙니다")

        val user = userRepository.findById(userId)
            ?: throw NotFoundException("사용자", userId)
        val newScheduledAt = request.scheduledAt ?: schedule.scheduledAt
        if (request.scheduledAt != null) {
            if (newScheduledAt.isBefore(LocalDateTime.now(KST))) {
                throw IllegalArgumentException("예약 시간은 현재 시간 이후여야 합니다")
            }
            validateScheduleLimit(user.planType, newScheduledAt)
        }

        val updated = schedule.copy(
            scheduledAt = newScheduledAt,
            platforms = request.platforms?.associate { config ->
                config.platform.name to mapOf("scheduledAt" to (config.scheduledAt ?: newScheduledAt).toString())
            } ?: schedule.platforms
        )
        scheduleRepository.update(updated)
        val video = videoRepository.findById(schedule.videoId)
        return updated.toResponse(video?.title, video?.thumbnailUrls?.firstOrNull())
    }

    @Transactional
    fun cancelSchedule(userId: Long, scheduleId: Long) {
        val schedule = scheduleRepository.findById(scheduleId) ?: throw NotFoundException("예약", scheduleId)
        if (schedule.userId != userId) throw ForbiddenException("해당 예약에 대한 권한이 없습니다")
        scheduleRepository.update(schedule.copy(status = ScheduleStatus.CANCELLED))
    }

    private fun validateScheduleLimit(planType: PlanType, scheduledAt: LocalDateTime) {
        if (planType == PlanType.FREE) throw PlanLimitExceededException("예약 업로드", 0)
        val maxDays = planType.scheduleDays.toLong()
        val nowKst = LocalDateTime.now(KST)
        if (scheduledAt.isAfter(nowKst.plusDays(maxDays))) {
            throw PlanLimitExceededException("예약 기간", planType.scheduleDays)
        }
    }

    private fun Schedule.toResponse(videoTitle: String?, thumbnailUrl: String?): ScheduleResponse {
        val platformConfigs = platforms.map { (key, _) ->
            PlatformScheduleConfig(
                platform = safeValueOfOrThrow<com.ongo.common.enums.Platform>(key),
                scheduledAt = scheduledAt
            )
        }
        return ScheduleResponse(
            id = id!!,
            videoId = videoId,
            videoTitle = videoTitle,
            thumbnailUrl = thumbnailUrl,
            scheduledAt = scheduledAt,
            status = status,
            platforms = platformConfigs,
            createdAt = createdAt
        )
    }
}
