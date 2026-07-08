package com.ongo.application.schedule

import com.ongo.application.video.StreamPublishUseCase
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 예약 게시 상태 모니터링.
 *
 * onGo는 영상을 직접 저장하지 않으므로, 업로드 시점에 플랫폼 네이티브 스케줄링
 * (YouTube publishAt, TikTok schedule_time 등)을 활용하여 즉시 업로드합니다.
 *
 * 이 스케줄러는 플랫폼에 예약 상태로 업로드된 영상의 실제 게시 상태를
 * 주기적으로 확인하여 Schedule 레코드의 상태를 동기화합니다.
 */
@Component
class ScheduleExecutor(
    private val scheduleRepository: ScheduleRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val distributedLockPort: DistributedLockPort,
    private val streamPublishUseCase: StreamPublishUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }

    private val lockId = javaClass.name.hashCode().toLong()

    /**
     * 예약 시간이 지난 SCHEDULED 상태의 레코드를 확인하여 상태를 동기화합니다.
     * - 플랫폼 업로드가 PUBLISHED → Schedule도 PUBLISHED
     * - 플랫폼 업로드가 FAILED → Schedule도 FAILED
     * - 아직 PROCESSING 중 → 다음 주기에 재확인
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    fun syncScheduleStatuses() {
        if (!distributedLockPort.tryLock(lockId)) {
            log.debug("다른 인스턴스에서 예약 상태 동기화 실행 중, 스킵")
            return
        }
        try {
            val now = LocalDateTime.now(KST)
            val dueSchedules = scheduleRepository.findDueSchedules(now)

            if (dueSchedules.isEmpty()) return
            log.debug("상태 확인할 예약 {}건", dueSchedules.size)

            dueSchedules.forEach { schedule ->
                try {
                    // 24시간 이상 상태 변화 없으면 타임아웃 처리
                    val timeoutHours = 24L
                    val isTimedOut = schedule.scheduledAt.plusHours(timeoutHours).isBefore(now)

                    val uploads = videoUploadRepository.findByVideoId(schedule.videoId)

                    // 아직 업로드가 시작되지 않은 예약 — 업로드 트리거
                    if (uploads.isEmpty() && schedule.status == ScheduleStatus.SCHEDULED) {
                        log.info("예약 업로드 트리거 [scheduleId={}, videoId={}]", schedule.id, schedule.videoId)
                        streamPublishUseCase.executeScheduledUpload(schedule)
                        return@forEach
                    }

                    if (uploads.isEmpty()) {
                        if (isTimedOut) {
                            scheduleRepository.update(schedule.copy(status = ScheduleStatus.FAILED))
                            log.error("예약 타임아웃 — 업로드 레코드 없음 [scheduleId={}, videoId={}]", schedule.id, schedule.videoId)
                        } else {
                            log.debug("예약 {}에 대한 업로드 레코드가 아직 없습니다 [videoId={}]", schedule.id, schedule.videoId)
                        }
                        return@forEach
                    }

                    val newStatus = when {
                        uploads.all { it.status == UploadStatus.PUBLISHED } -> ScheduleStatus.PUBLISHED
                        uploads.all { it.status == UploadStatus.FAILED || it.status == UploadStatus.REJECTED } -> ScheduleStatus.FAILED
                        uploads.any { it.status == UploadStatus.PROCESSING || it.status == UploadStatus.REVIEW } -> ScheduleStatus.PROCESSING
                        isTimedOut -> {
                            log.error("예약 타임아웃 — {}시간 이상 완료되지 않음 [scheduleId={}]", timeoutHours, schedule.id)
                            ScheduleStatus.FAILED
                        }
                        else -> null // 아직 업로드 진행 중 — 다음 주기에 재확인
                    }

                    if (newStatus != null && newStatus != schedule.status) {
                        scheduleRepository.update(schedule.copy(status = newStatus))
                        log.info("예약 상태 갱신 [scheduleId={}, {} → {}]", schedule.id, schedule.status, newStatus)
                    }
                } catch (e: Exception) {
                    log.error("예약 상태 동기화 실패 [scheduleId={}]: {}", schedule.id, e.message, e)
                }
            }
        } finally {
            distributedLockPort.releaseLock(lockId)
        }
    }
}
