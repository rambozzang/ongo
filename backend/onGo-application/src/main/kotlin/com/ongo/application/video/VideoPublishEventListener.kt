package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.LocalDateTime
import java.util.concurrent.Executors

@Component
class VideoPublishEventListener(
    private val platformUploadServices: List<PlatformUploadService>,
    private val videoUploadRepository: VideoUploadRepository,
    private val videoRepository: VideoRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleVideoPublish(event: VideoPublishEvent) {
        log.info("영상 게시 이벤트 수신: videoId={}, platforms={}", event.videoId,
            event.platformConfigs.map { it.platform })

        // Virtual Thread 기반 병렬 업로드
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val futures = event.platformConfigs.map { config ->
                executor.submit<Unit> {
                    uploadToPlatform(event, config)
                }
            }

            // 모든 플랫폼 업로드 완료 대기
            futures.forEach { future ->
                try {
                    future.get()
                } catch (e: Exception) {
                    log.error("플랫폼 업로드 작업 실행 중 오류", e)
                }
            }
        }

        // 모든 플랫폼 업로드 완료 후, 영상 전체 상태 업데이트
        updateOverallVideoStatus(event.videoId)
    }

    private fun uploadToPlatform(event: VideoPublishEvent, config: PlatformUploadConfig) {
        val service = platformUploadServices.find { it.supports(config.platform) }
        if (service == null) {
            log.error("플랫폼 {} 에 대한 업로드 서비스를 찾을 수 없습니다", config.platform)
            updateUploadStatus(
                config.videoUploadId,
                UploadStatus.FAILED,
                "지원되지 않는 플랫폼: ${config.platform}",
            )
            fireCompletedEvent(event, config.platform, false, errorMessage = "지원되지 않는 플랫폼")
            return
        }

        try {
            log.info("플랫폼 {} 업로드 시작: videoId={}", config.platform, event.videoId)
            val result = service.upload(config, event.fileUrl, event.userId)

            if (result.success) {
                updateUploadStatus(
                    config.videoUploadId,
                    UploadStatus.PROCESSING,
                    platformVideoId = result.platformVideoId,
                    platformUrl = result.platformUrl,
                )
                fireCompletedEvent(event, config.platform, true, platformUrl = result.platformUrl)
                log.info("플랫폼 {} 업로드 성공: videoId={}, platformUrl={}", config.platform, event.videoId, result.platformUrl)
            } else {
                updateUploadStatus(config.videoUploadId, UploadStatus.FAILED, result.errorMessage)
                fireCompletedEvent(event, config.platform, false, errorMessage = result.errorMessage)
                log.warn("플랫폼 {} 업로드 실패: videoId={}, error={}", config.platform, event.videoId, result.errorMessage)
            }
        } catch (e: Exception) {
            log.error("플랫폼 {} 업로드 중 예외 발생: videoId={}", config.platform, event.videoId, e)
            updateUploadStatus(config.videoUploadId, UploadStatus.FAILED, e.message)
            fireCompletedEvent(event, config.platform, false, errorMessage = e.message)
        }
    }

    private fun updateUploadStatus(
        uploadId: Long,
        status: UploadStatus,
        errorMessage: String? = null,
        platformVideoId: String? = null,
        platformUrl: String? = null,
    ) {
        val upload = videoUploadRepository.findById(uploadId) ?: return
        videoUploadRepository.update(
            upload.copy(
                status = status,
                errorMessage = errorMessage,
                platformVideoId = platformVideoId ?: upload.platformVideoId,
                platformUrl = platformUrl ?: upload.platformUrl,
                publishedAt = if (status == UploadStatus.PUBLISHED) LocalDateTime.now() else upload.publishedAt,
            )
        )
    }

    private fun updateOverallVideoStatus(videoId: Long) {
        val uploads = videoUploadRepository.findByVideoId(videoId)
        if (uploads.isEmpty()) return

        val video = videoRepository.findById(videoId) ?: return

        val overallStatus = when {
            uploads.all { it.status == UploadStatus.PUBLISHED } -> UploadStatus.PUBLISHED
            uploads.all { it.status == UploadStatus.FAILED || it.status == UploadStatus.REJECTED } -> UploadStatus.FAILED
            uploads.any { it.status == UploadStatus.PROCESSING || it.status == UploadStatus.REVIEW } -> UploadStatus.PROCESSING
            else -> UploadStatus.UPLOADING
        }

        videoRepository.update(video.copy(status = overallStatus))
    }

    private fun fireCompletedEvent(
        event: VideoPublishEvent,
        platform: Platform,
        success: Boolean,
        platformUrl: String? = null,
        errorMessage: String? = null,
    ) {
        eventPublisher.publishEvent(
            UploadCompletedEvent(
                videoId = event.videoId,
                userId = event.userId,
                platform = platform,
                success = success,
                platformUrl = platformUrl,
                errorMessage = errorMessage,
            )
        )
    }
}
