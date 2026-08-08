package com.ongo.application.recurring

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.recurring.RecurringSchedule
import com.ongo.domain.recurring.RecurringScheduleRepository
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId

/** Turns a due recurring definition into an ordinary durable schedule. */
@Component
class RecurringScheduleExecutor(
    private val recurringRepository: RecurringScheduleRepository,
    private val recurringUseCase: RecurringScheduleUseCase,
    private val scheduleRepository: ScheduleRepository,
    private val videoRepository: VideoRepository,
    private val distributedLockPort: DistributedLockPort,
    private val userWriteGuard: UserWriteGuard,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val clockZone = ZoneId.of("Asia/Seoul")

    @Scheduled(fixedDelayString = "\${recurring.schedule.delay-ms:30000}")
    fun executeDueSchedules() {
        val ran = distributedLockPort.withLock(javaClass.name.hashCode().toLong()) {
            recurringRepository.findDue(LocalDateTime.now(clockZone)).forEach { execute(it) }
        }
        if (!ran) log.debug("다른 인스턴스에서 반복 예약 실행 중, 스킵")
    }

    private fun execute(definition: RecurringSchedule) {
        val id = definition.id ?: return
        val occurrence = definition.nextRunAt ?: return
        val next = recurringUseCase.nextRunAtAfter(definition, occurrence)

        runCatching { userWriteGuard.requireWritable(definition.userId) }
            .onFailure { log.info("동결된 계정의 반복 예약을 보류합니다. recurringId={}", id) }
            .getOrElse { return }

        // Advance first: a crash may skip one occurrence, but can never publish it twice.
        if (!recurringRepository.markRun(id, occurrence, occurrence, next)) return

        try {
            val sourceId = definition.videoId
            val source = sourceId?.let(videoRepository::findById)
            val platforms = definition.platforms.mapNotNull { it.toPlatformOrNull() }.distinct()
            if (source == null || source.fileUrl.isNullOrBlank() || platforms.isEmpty()) {
                log.warn(
                    "반복 예약을 건너뜁니다: source video/file/platform 설정이 없습니다. recurringId={}, videoId={}, platforms={}",
                    id, sourceId, platforms,
                )
                return
            }

            // video_uploads has a deliberate unique(video_id, platform) key. Each
            // occurrence therefore gets its own library row while retaining the
            // original media URL and making every occurrence independently visible.
            val occurrenceVideo = videoRepository.save(
                source.copy(
                    id = null,
                    title = definition.titleTemplate?.takeIf { it.isNotBlank() } ?: source.title,
                    description = definition.descriptionTemplate ?: source.description,
                    tags = if (definition.tags.isEmpty()) source.tags else definition.tags,
                    status = UploadStatus.DRAFT,
                    createdAt = null,
                    updatedAt = null,
                )
            )
            val scheduledAt = occurrence.atZone(ZoneId.of(definition.timezone))
                .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
                .toLocalDateTime()
            scheduleRepository.save(
                Schedule(
                    videoId = occurrenceVideo.id!!,
                    userId = definition.userId,
                    scheduledAt = scheduledAt,
                    platforms = platforms.associate { platform ->
                        platform.name to mapOf("scheduledAt" to scheduledAt.toString(), "recurringId" to id)
                    },
                )
            )
            log.info("반복 예약 실행을 생성했습니다. recurringId={}, videoId={}, scheduledAt={}", id, occurrenceVideo.id, scheduledAt)
        } catch (error: Exception) {
            log.error("반복 예약 실행 생성 실패. recurringId={}, occurrence={}", id, occurrence, error)
        }
    }

    private fun String.toPlatformOrNull(): Platform? =
        runCatching { Platform.valueOf(this.uppercase()) }.getOrNull()
}
