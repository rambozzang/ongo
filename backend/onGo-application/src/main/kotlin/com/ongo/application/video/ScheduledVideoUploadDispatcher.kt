package com.ongo.application.video

import com.ongo.common.exception.AccountFrozenException
import com.ongo.domain.accountdeletion.UserWriteGuard
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
    private val storageService: StorageService,
    private val userWriteGuard: UserWriteGuard,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${video.publish.dispatch-delay-ms:15000}")
    fun dispatchDueUploads() {
        videoUploadRepository.findDueScheduledUploads(LocalDateTime.now()).forEach { upload ->
            val video = videoRepository.findById(upload.videoId)
            // DB에 저장된 URL이 presigned URL인 경우 예약 기간 중 만료될 수 있다.
            // due 시점에 새 URL을 발급해 외부 Graph/API가 실제 파일을 읽게 한다.
            val fileUrl = video?.let { current ->
                runCatching { storageService.getFileUrl(current.id!!) }.getOrNull() ?: current.fileUrl
            }
            val uploadId = upload.id
            if (video == null || fileUrl.isNullOrBlank() || uploadId == null) {
                log.warn("예약 게시 작업을 깨울 수 없습니다: videoId={}, uploadId={}", upload.videoId, uploadId)
                return@forEach
            }
            try {
                userWriteGuard.requireWritable(video.userId)
            } catch (_: AccountFrozenException) {
                // A scheduled upload is still an external write. Do not let this
                // independent dispatcher bypass the deletion/freeze guard used by
                // ScheduleExecutor; the row remains queued for an explicit retry.
                log.info("동결된 계정의 예약 게시를 보류합니다. videoId={}, uploadId={}", video.id, uploadId)
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
                            // durable queue가 due 작업을 깨웠으므로 플랫폼에는 과거 시각을
                            // 다시 전달하지 않는다. 여기부터는 즉시 게시 호출이다.
                            scheduledAt = null,
                        )
                    ),
                )
            )
        }
    }
}
