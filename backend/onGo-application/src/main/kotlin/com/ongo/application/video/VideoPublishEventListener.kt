package com.ongo.application.video

import com.ongo.application.config.ExecutorConfig
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

        // Virtual Thread 기반 병렬 업로드 (Semaphore로 동시 실행 제한)
        ExecutorConfig.newVirtualExecutor().use { executor ->
            val futures = event.platformConfigs.map { config ->
                executor.submit<Unit> {
                    ExecutorConfig.uploadSemaphore.acquire()
                    try {
                        uploadToPlatform(event, config)
                    } finally {
                        ExecutorConfig.uploadSemaphore.release()
                    }
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

        val fileUrl = event.fileUrl
        if (fileUrl == null) {
            log.warn("영상 {} 에 fileUrl이 없어 플랫폼 업로드를 건너뜁니다 (스트리밍 업로드)", event.videoId)
            updateUploadStatus(config.videoUploadId, UploadStatus.FAILED, "파일 URL이 없습니다. 스트리밍 방식으로 업로드된 영상입니다.")
            return
        }

        try {
            log.info("플랫폼 {} 업로드 시작: videoId={}", config.platform, event.videoId)
            val result = service.upload(config, fileUrl, event.userId)

            if (result.success) {
                // 플랫폼이 게시를 확정했으면 PUBLISHED 로 끝낸다. 확정 신호가 없는
                // 플랫폼만 PROCESSING 에 남아 후속 확인을 기다린다.
                updateUploadStatus(
                    config.videoUploadId,
                    if (result.published) UploadStatus.PUBLISHED else UploadStatus.PROCESSING,
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
            // 외부 플랫폼 호출은 타임아웃 시 이미 게시가 완료됐을 가능성이 있다.
            // 무조건 FAILED로 기록하면 사용자가 재시도하여 중복 게시를 만들 수 있으므로
            // 확인 불가 상태로 남기고, 후속 조회/운영 재검증 대상으로 보낸다.
            updateUploadStatus(config.videoUploadId, UploadStatus.UNCONFIRMED, e.message)
            fireCompletedEvent(event, config.platform, false, errorMessage = "게시 결과 확인 필요: ${e.message}")
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
            uploads.any { it.status == UploadStatus.PUBLISHED } && uploads.any {
                it.status == UploadStatus.FAILED || it.status == UploadStatus.REJECTED ||
                    it.status == UploadStatus.UNCONFIRMED
            } -> UploadStatus.PARTIALLY_PUBLISHED
            uploads.all { it.status == UploadStatus.FAILED || it.status == UploadStatus.REJECTED } -> UploadStatus.FAILED
            uploads.all { it.status == UploadStatus.UNCONFIRMED } -> UploadStatus.UNCONFIRMED
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
