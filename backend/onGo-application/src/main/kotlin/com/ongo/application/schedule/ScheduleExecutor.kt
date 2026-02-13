package com.ongo.application.schedule

import com.ongo.application.video.PlatformUploadConfig
import com.ongo.application.video.VideoPublishEvent
import com.ongo.common.enums.Platform
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.Visibility
import com.ongo.common.util.safeValueOfOrThrow
import com.ongo.domain.event.ScheduleFailedEvent
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.domain.video.VideoPlatformMetaRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoPlatformMeta
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class ScheduleExecutor(
    private val scheduleRepository: ScheduleRepository,
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val videoPlatformMetaRepository: VideoPlatformMetaRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    fun executeDueSchedules() {
        val now = LocalDateTime.now(KST)
        val dueSchedules = scheduleRepository.findDueSchedules(now)

        if (dueSchedules.isEmpty()) return
        log.info("실행할 예약 ${dueSchedules.size}건 발견")

        dueSchedules.forEach { schedule ->
            try {
                scheduleRepository.update(schedule.copy(status = ScheduleStatus.PROCESSING))

                val video = videoRepository.findById(schedule.videoId)
                if (video == null) {
                    log.error("예약된 영상을 찾을 수 없습니다 [videoId=${schedule.videoId}]")
                    scheduleRepository.update(schedule.copy(status = ScheduleStatus.FAILED))
                    return@forEach
                }

                val fileUrl = video.fileUrl
                if (fileUrl.isNullOrBlank()) {
                    log.error("파일 업로드가 완료되지 않은 영상입니다 [videoId=${schedule.videoId}]")
                    scheduleRepository.update(schedule.copy(status = ScheduleStatus.FAILED))
                    return@forEach
                }

                val platforms = schedule.platforms.keys.map { safeValueOfOrThrow<Platform>(it) }

                // Create VideoUpload + PlatformMeta for each platform, build configs
                val platformConfigs = platforms.map { platform ->
                    val upload = videoUploadRepository.save(
                        VideoUpload(
                            videoId = schedule.videoId,
                            platform = platform,
                            status = UploadStatus.UPLOADING,
                        )
                    )
                    val uploadId = upload.id!!

                    videoPlatformMetaRepository.save(
                        VideoPlatformMeta(
                            videoUploadId = uploadId,
                            title = video.title,
                            description = video.description,
                            tags = video.tags,
                            visibility = Visibility.PUBLIC,
                        )
                    )

                    PlatformUploadConfig(
                        platform = platform,
                        videoUploadId = uploadId,
                        title = video.title,
                        description = video.description,
                        tags = video.tags,
                        visibility = Visibility.PUBLIC,
                        thumbnailUrl = video.thumbnailUrls.firstOrNull(),
                        scheduledAt = schedule.scheduledAt,
                    )
                }

                // Update video status
                videoRepository.update(video.copy(status = UploadStatus.UPLOADING))

                eventPublisher.publishEvent(
                    VideoPublishEvent(
                        videoId = schedule.videoId,
                        userId = schedule.userId,
                        fileUrl = fileUrl,
                        platformConfigs = platformConfigs,
                    )
                )
                scheduleRepository.update(schedule.copy(status = ScheduleStatus.PUBLISHED))
                log.info("예약 실행 완료 [scheduleId=${schedule.id}, videoId=${schedule.videoId}]")
            } catch (e: Exception) {
                log.error("예약 실행 실패 [scheduleId=${schedule.id}]: ${e.message}", e)
                scheduleRepository.update(schedule.copy(status = ScheduleStatus.FAILED))
                eventPublisher.publishEvent(
                    ScheduleFailedEvent(
                        scheduleId = schedule.id!!,
                        videoId = schedule.videoId,
                        userId = schedule.userId,
                        reason = e.message ?: "알 수 없는 오류"
                    )
                )
            }
        }
    }
}
