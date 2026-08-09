package com.ongo.application.schedule

import com.ongo.application.schedule.dto.*
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.common.util.safeValueOfOrThrow
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class ScheduleUseCase(
    private val scheduleRepository: ScheduleRepository,
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val userRepository: UserRepository,
    private val userWriteGuard: UserWriteGuard,
) {
    companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }

    @Transactional
    fun createSchedule(userId: Long, request: CreateScheduleRequest): ScheduleResponse {
        userWriteGuard.requireWritable(userId)
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        val video = videoRepository.findById(request.videoId) ?: throw NotFoundException("영상", request.videoId)
        if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 권한이 없습니다")

        // 과거 시간 예약 방지
        val nowKst = LocalDateTime.now(KST)
        validatePlatformScheduleTimes(user.planType, request.scheduledAt, request.platforms, nowKst)

        val platformConfigs = request.platforms.associate { config ->
            scheduleKey(config.platform, config.channelId) to mapOf("scheduledAt" to (config.scheduledAt ?: request.scheduledAt).toString())
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
        userWriteGuard.requireWritable(userId)
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
            require(schedule.platforms.keys == it.map { config -> scheduleKey(config.platform, config.channelId) }.toSet()) {
                "예약 플랫폼 변경은 새 게시 작업으로 생성해주세요. 기존 예약은 플랫폼별 시간만 수정할 수 있습니다."
            }
            validatePlatformScheduleTimes(user.planType, newScheduledAt, it, LocalDateTime.now(KST))
        }

        val updatedPlatformTimes = when {
            request.platforms != null -> request.platforms.associate { config ->
                scheduleKey(config.platform, config.channelId) to mapOf("scheduledAt" to (config.scheduledAt ?: newScheduledAt).toString())
            }
            request.scheduledAt != null -> {
                // Calendar drag sends only the parent time. Move every platform
                // by the same delta so per-platform offsets and the durable
                // queue remain consistent.
                val delta = Duration.between(schedule.scheduledAt, newScheduledAt)
                schedule.platforms.mapValues { (_, raw) ->
                    val platformTime = raw.asPlatformScheduleTime() ?: schedule.scheduledAt
                    mapOf("scheduledAt" to platformTime.plus(delta).toString())
                }
            }
            else -> schedule.platforms
        }
        val updated = schedule.copy(
            scheduledAt = newScheduledAt,
            platforms = updatedPlatformTimes,
        )
        val uploads = videoUploadRepository.findByVideoId(schedule.videoId)
        val uploadTimes = updated.platforms.mapNotNull { (key, raw) ->
            val (platform, channelId) = parseScheduleKey(key) ?: return@mapNotNull null
            val time = raw.asPlatformScheduleTime()
            val upload = uploads.firstOrNull { it.platform == platform && it.channelId == channelId }
            upload?.id?.let { id -> if (time != null) id to time else null }
        }.toMap()
        videoUploadRepository.rescheduleScheduledUploads(schedule.videoId, uploadTimes)
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
        userWriteGuard.requireWritable(userId)
        val schedule = scheduleRepository.findById(scheduleId) ?: throw NotFoundException("예약", scheduleId)
        if (schedule.userId != userId) throw ForbiddenException("해당 예약에 대한 권한이 없습니다")
        if (schedule.status != ScheduleStatus.SCHEDULED) {
            throw IllegalStateException("아직 실행되지 않은 예약만 취소할 수 있습니다. 현재 상태: ${schedule.status}")
        }
        // Schedule만 취소하면 durable upload queue가 남아 dispatcher가 외부
        // 플랫폼에 게시한다. 같은 트랜잭션에서 아직 전송되지 않은 자식 작업도
        // CANCELLED로 바꿔 취소 의도를 큐까지 전파한다.
        videoUploadRepository.cancelScheduledUploads(schedule.videoId, LocalDateTime.now())
        scheduleRepository.update(schedule.copy(status = ScheduleStatus.CANCELLED))
        val video = videoRepository.findById(schedule.videoId)
        val uploads = videoUploadRepository.findByVideoId(schedule.videoId)
        if (video != null && uploads.isNotEmpty()) {
            val nextVideoStatus = when {
                uploads.all { it.status == UploadStatus.CANCELLED } -> UploadStatus.DRAFT
                uploads.any { it.status == UploadStatus.PUBLISHED } && uploads.all {
                    it.status == UploadStatus.PUBLISHED || it.status == UploadStatus.CANCELLED
                } -> UploadStatus.PARTIALLY_PUBLISHED
                else -> null
            }
            if (nextVideoStatus != null && nextVideoStatus != video.status) {
                videoRepository.update(video.copy(status = nextVideoStatus))
            }
        }
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
        require(platforms.map { scheduleKey(it.platform, it.channelId) }.distinct().size == platforms.size) {
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
            val (platform, channelId) = parseScheduleKey(key)
                ?: throw IllegalArgumentException("잘못된 예약 플랫폼 키입니다: $key")
            // A video can have more than one schedule. Match the upload by its
            // platform-specific scheduled time instead of leaking the result
            // from another occurrence into this calendar item.
            val upload = uploadsByPlatform[platform]
                ?.firstOrNull { it.channelId == channelId && it.scheduledAt == platformScheduledAt }
            PlatformScheduleConfig(
                platform = platform,
                channelId = channelId,
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
        UploadStatus.CANCELLED -> ScheduleStatus.CANCELLED
        UploadStatus.PROCESSING, UploadStatus.REVIEW -> ScheduleStatus.PROCESSING
        UploadStatus.DRAFT, UploadStatus.UPLOADING, UploadStatus.IMPORTING -> ScheduleStatus.SCHEDULED
    }

    private fun Any?.asPlatformScheduleTime(): LocalDateTime? {
        val raw = (this as? Map<*, *>)?.get("scheduledAt")?.toString() ?: return null
        return runCatching { LocalDateTime.parse(raw) }.getOrNull()
    }

    private fun scheduleKey(platform: com.ongo.common.enums.Platform, channelId: Long?): String =
        if (channelId == null) platform.name else "${platform.name}#$channelId"

    private fun parseScheduleKey(key: String): Pair<com.ongo.common.enums.Platform, Long?>? {
        val parts = key.split('#', limit = 2)
        val platform = runCatching { com.ongo.common.enums.Platform.valueOf(parts[0]) }.getOrNull() ?: return null
        val channelId = parts.getOrNull(1)?.toLongOrNull()
        if (parts.size > 1 && channelId == null) return null
        return platform to channelId
    }
}
