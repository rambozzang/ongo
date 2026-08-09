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
import com.ongo.application.video.StorageService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
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
    private val storageService: StorageService,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val clockZone = ZoneId.of("Asia/Seoul")
    private val perOccurrenceTx = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    @Scheduled(fixedDelayString = "\${recurring.schedule.delay-ms:30000}")
    fun executeDueSchedules() {
        val ran = distributedLockPort.withLock(javaClass.name.hashCode().toLong()) {
            recurringRepository.findDue(LocalDateTime.now(clockZone)).forEach { definition ->
                try {
                    // 다음 실행 시각 갱신과 회차 Schedule 생성을 원자적으로
                    // 커밋한다. 둘 사이에 프로세스가 죽으면 회차가 유실된다.
                    perOccurrenceTx.executeWithoutResult { execute(definition) }
                } catch (error: Exception) {
                    // 트랜잭션 롤백으로 due 행은 그대로 남고 다음 주기에 재시도된다.
                    log.error(
                        "반복 예약 회차 생성 실패. recurringId={}, nextRunAt={}",
                        definition.id,
                        definition.nextRunAt,
                        error,
                    )
                }
            }
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

        val sourceId = definition.videoId
        val source = sourceId?.let(videoRepository::findById)
        val targetKeys = definition.platforms.mapNotNull { it.toTargetKeyOrNull() }.distinct()
        if (source == null || source.fileUrl.isNullOrBlank() || targetKeys.isEmpty()) {
            throw IllegalStateException(
                "반복 예약 원본 영상/파일/플랫폼 설정이 없습니다: recurringId=$id videoId=$sourceId platforms=$targetKeys",
            )
        }

        // video_uploads has a deliberate unique(video_id, platform) key. Each
        // occurrence therefore gets its own library row. The media object is
        // copied to the occurrence prefix below so its URL remains refreshable
        // after the original row or its presigned URL changes.
        val occurrenceVideo = videoRepository.save(
            source.copy(
                id = null,
                fileUrl = null,
                title = definition.titleTemplate?.takeIf { it.isNotBlank() } ?: source.title,
                description = definition.descriptionTemplate ?: source.description,
                tags = if (definition.tags.isEmpty()) source.tags else definition.tags,
                status = UploadStatus.DRAFT,
                createdAt = null,
                updatedAt = null,
            )
        )
        val occurrenceVideoId = requireNotNull(occurrenceVideo.id) { "반복 회차 영상 생성에 실패했습니다." }
        val durableFileUrl = try {
            storageService.copyVideoFile(requireNotNull(source.id), occurrenceVideoId, source.fileUrl)
        } catch (error: Exception) {
            // The database transaction will roll back, but an object-store copy
            // cannot be rolled back by PostgreSQL. Remove the destination so a
            // failed occurrence never leaks storage or quota.
            runCatching { storageService.deleteFile(occurrenceVideoId) }
                .onFailure { cleanupError -> log.error("반복 회차 파일 보상 삭제 실패. videoId={}", occurrenceVideoId, cleanupError) }
            throw error
        }
        val durableOccurrenceVideo = videoRepository.update(occurrenceVideo.copy(fileUrl = durableFileUrl))
        // nextRunAt is persisted in the scheduler's shared KST storage zone.
        val scheduledAt = occurrence
        scheduleRepository.save(
            Schedule(
                videoId = durableOccurrenceVideo.id!!,
                userId = definition.userId,
                scheduledAt = scheduledAt,
                platforms = targetKeys.associate { targetKey ->
                    targetKey to mapOf("scheduledAt" to scheduledAt.toString(), "recurringId" to id)
                },
            )
        )
        // Consume the occurrence only after the copied video and schedule are
        // both persisted. Any earlier failure rolls back and remains due.
        if (!recurringRepository.markRun(id, occurrence, occurrence, next)) {
            throw IllegalStateException("반복 예약 회차 선점 상태가 변경되었습니다: recurringId=$id")
        }
        log.info("반복 예약 실행을 생성했습니다. recurringId={}, videoId={}, scheduledAt={}", id, occurrenceVideo.id, scheduledAt)
    }

    private fun String.toTargetKeyOrNull(): String? {
        val platformName = substringBefore('#').uppercase()
        val channelPart = substringAfter('#', "")
        if (channelPart.isNotBlank() && channelPart.toLongOrNull()?.let { it > 0 } != true) return null
        return runCatching { Platform.valueOf(platformName) }.getOrNull()?.let {
            if (channelPart.isBlank()) it.name else "${it.name}#${channelPart.toLong()}"
        }
    }
}
