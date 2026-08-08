package com.ongo.application.video

import com.ongo.domain.video.VideoPlatformMetaRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/** 예약 시각이 된 durable upload만 외부 게시 이벤트로 깨운다. */
@Component
class ScheduledVideoUploadDispatcher(
    private val videoUploadRepository: VideoUploadRepository,
    private val videoRepository: VideoRepository,
    private val videoPlatformMetaRepository: VideoPlatformMetaRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${video.publish.dispatch-delay-ms:15000}")
    fun dispatchDueUploads() {
        videoUploadRepository.findDueScheduledUploads(LocalDateTime.now()).forEach { upload ->
            val video = videoRepository.findById(upload.videoId)
            val fileUrl = video?.fileUrl
            val uploadId = upload.id
            if (video == null || fileUrl.isNullOrBlank() || uploadId == null) {
                log.warn("예약 게시 작업을 깨울 수 없습니다: videoId={}, uploadId={}", upload.videoId, uploadId)
                return@forEach
            }
            val meta = videoPlatformMetaRepository.findByVideoUploadId(uploadId)
            eventPublisher.publishEvent(
                VideoPublishEvent(
                    videoId = video.id!!,
                    userId = video.userId,
                    fileUrl = fileUrl,
                    platformConfigs = listOf(
                        PlatformUploadConfig(
                            platform = upload.platform,
                            videoUploadId = uploadId,
                            title = meta?.title ?: video.title,
                            description = meta?.description,
                            tags = meta?.tags ?: emptyList(),
                            visibility = meta?.visibility ?: com.ongo.common.enums.Visibility.PUBLIC,
                            thumbnailUrl = meta?.customThumbnailUrl,
                            scheduledAt = upload.scheduledAt,
                        )
                    ),
                )
            )
        }
    }
}
