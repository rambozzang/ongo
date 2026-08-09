package com.ongo.application.schedule

import com.ongo.application.schedule.dto.*
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.common.util.safeValueOfOrThrow
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class ScheduleUseCase(
    private val scheduleRepository: ScheduleRepository,
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
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
        validatePlatformScheduleTimes(user.planType, request.scheduledAt, request.platforms, nowKst)

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
        return saved.toResponse(
            video.title,
            video.thumbnailUrls.firstOrNull(),
            videoUploadRepository.findByVideoId(request.videoId),
        )
    }

    fun getSchedules(
        userId: Long,
        from: LocalDateTime?,
        to: LocalDateTime?,
        status: String? = null,
    ): ScheduleCalendarResponse {
        val effectiveFrom = from ?: LocalDateTime.now(KST).withDayOfMonth(1).withHour(0).withMinute(0)
        val effectiveTo = to ?: effectiveFrom.plusMonths(1)
        val requestedStatus = parseStatus(status)

        val schedules = scheduleRepository.findByUserIdAndDateRange(userId, effectiveFrom, effectiveTo)
            .filter { requestedStatus == null || it.status == requestedStatus }
        val videoIds = schedules.map { it.videoId }.distinct()
        val videosById = videoRepository.findByIds(videoIds).associateBy { it.id }
        val uploadsByVideoId = videoUploadRepository.findByVideoIds(videoIds)
        return ScheduleCalendarResponse(
            schedules = schedules.map { schedule ->
                val video = videosById[schedule.videoId]
                schedule.toResponse(
                    video?.title,
                    video?.thumbnailUrls?.firstOrNull(),
                    uploadsByVideoId[schedule.videoId].orEmpty(),
                )
            },
            from = effectiveFrom,
            to = effectiveTo
        )
    }

    fun getSchedule(userId: Long, scheduleId: Long): ScheduleResponse {
        val schedule = scheduleRepository.findById(scheduleId) ?: throw NotFoundException("예약", scheduleId)
        if (schedule.userId != userId) throw ForbiddenException("해당 예약에 대한 권한이 없습니다")
        val video = videoRepository.findById(schedule.videoId)
        val uploads = videoUploadRepository.findByVideoId(schedule.videoId)
        return schedule.toResponse(video?.title, video?.thumbnailUrls?.firstOrNull(), uploads)
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
        request.platforms?.let {
            validatePlatformScheduleTimes(user.planType, newScheduledAt, it, LocalDateTime.now(KST))
        }

        val updated = schedule.copy(
            scheduledAt = newScheduledAt,
            platforms = request.platforms?.associate { config ->
                config.platform.name to mapOf("scheduledAt" to (config.scheduledAt ?: newScheduledAt).toString())
            } ?: schedule.platforms
        )
        scheduleRepository.update(updated)
        val video = videoRepository.findById(schedule.videoId)
        return updated.toResponse(
            video?.title,
            video?.thumbnailUrls?.firstOrNull(),
            videoUploadRepository.findByVideoId(schedule.videoId),
        )
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

    private fun validatePlatformScheduleTimes(
        planType: PlanType,
        globalScheduledAt: LocalDateTime,
        platforms: List<PlatformScheduleConfig>,
        nowKst: LocalDateTime,
    ) {
        require(platforms.isNotEmpty()) { "예약할 플랫폼을 하나 이상 선택해야 합니다" }
        require(platforms.map { it.platform }.distinct().size == platforms.size) {
            "예약 플랫폼은 중복될 수 없습니다"
        }

        platforms.forEach { config ->
            val scheduledAt = config.scheduledAt ?: globalScheduledAt
            if (scheduledAt.isBefore(nowKst)) {
                throw IllegalArgumentException("플랫폼별 예약 시간은 현재 시간 이후여야 합니다")
            }
            validateScheduleLimit(planType, scheduledAt)
        }
    }

    private fun parseStatus(raw: String?): ScheduleStatus? {
        if (raw.isNullOrBlank()) return null
        return runCatching { ScheduleStatus.valueOf(raw.trim().uppercase()) }
            .getOrElse { throw IllegalArgumentException("지원하지 않는 예약 상태입니다: $raw") }
    }

    private fun Schedule.toResponse(
        videoTitle: String?,
        thumbnailUrl: String?,
        uploads: List<VideoUpload>,
    ): ScheduleResponse {
        val uploadsByPlatform = uploads.groupBy { it.platform }
        val platformConfigs = platforms.map { (key, value) ->
            val platformScheduledAt = value.asPlatformScheduleTime() ?: scheduledAt
            val platform = safeValueOfOrThrow<com.ongo.common.enums.Platform>(key)
            // A video can have more than one schedule. Match the upload by its
            // platform-specific scheduled time instead of leaking the result
            // from another occurrence into this calendar item.
            val upload = uploadsByPlatform[platform]
                ?.firstOrNull { it.scheduledAt == platformScheduledAt }
            PlatformScheduleConfig(
                platform = platform,
                scheduledAt = platformScheduledAt,
                status = upload?.status?.toScheduleStatus(),
                platformUrl = upload?.platformUrl,
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

    private fun UploadStatus.toScheduleStatus(): ScheduleStatus = when (this) {
        UploadStatus.PUBLISHED -> ScheduleStatus.PUBLISHED
        UploadStatus.FAILED, UploadStatus.REJECTED, UploadStatus.IMPORT_FAILED -> ScheduleStatus.FAILED
        UploadStatus.UNCONFIRMED -> ScheduleStatus.UNCONFIRMED
        UploadStatus.PARTIALLY_PUBLISHED -> ScheduleStatus.PARTIALLY_PUBLISHED
        UploadStatus.PROCESSING, UploadStatus.REVIEW -> ScheduleStatus.PROCESSING
        UploadStatus.DRAFT, UploadStatus.UPLOADING, UploadStatus.IMPORTING -> ScheduleStatus.SCHEDULED
    }

    private fun Any?.asPlatformScheduleTime(): LocalDateTime? {
        val raw = (this as? Map<*, *>)?.get("scheduledAt")?.toString() ?: return null
        return runCatching { LocalDateTime.parse(raw) }.getOrNull()
    }
}
