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
    private val notificationRepository: com.ongo.domain.notification.NotificationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val clockZone = ZoneId.of("Asia/Seoul")

    /**
     * 요금제 때문에 반복 예약이 멈췄다고 **알린다.**
     *
     * 알리지 않으면 요금제를 낮추거나 결제 실패로 무료가 된 사용자의 게시가 조용히 멈추고,
     * 다시 올려도 꺼진 채로 남아 사용자는 왜 게시가 안 되는지 모른다. 반대로 알리면 이 순간은
     * **업그레이드를 권할 자리**가 된다.
     *
     * 알림 실패가 비활성화를 되돌리게 하지 않는다 — 한도를 넘는 게시를 막는 것이 우선이다.
     */
    private fun notifyStoppedByPlan(definition: RecurringSchedule) {
        runCatching {
            notificationRepository.save(
                com.ongo.domain.notification.Notification(
                    userId = definition.userId,
                    type = com.ongo.common.enums.NotificationType.SYSTEM,
                    title = "반복 예약이 멈췄습니다",
                    message = "현재 요금제에서는 '${definition.name}' 반복 예약을 실행할 수 없어 멈췄습니다. " +
                        "요금제를 업그레이드한 뒤 반복 예약에서 다시 켜 주세요.",
                    referenceType = "recurring_schedule",
                    referenceId = definition.id,
                ),
            )
        }.onFailure { log.warn("반복 예약 중단 알림 저장 실패. recurringId={}", definition.id, it) }
    }
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

        if (recurringUseCase.deactivateIfOutsideCurrentPlan(definition.userId, occurrence)) {
            recurringRepository.update(definition.copy(isActive = false))
            log.info("현재 플랜 예약 한도를 벗어난 반복 예약을 비활성화했습니다. recurringId={}", id)
            notifyStoppedByPlan(definition)
            return
        }

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
                // 회차 사본이다. source.copy 가 원본 출처를 그대로 가져오면 매 회차가 새 업로드로
                // 세어져, 반복 예약을 걸어 둔 사용자의 월 한도가 저절로 줄어든다(MonthlyUploadPolicy).
                source = com.ongo.domain.contentsource.VideoSource.DERIVED,
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
