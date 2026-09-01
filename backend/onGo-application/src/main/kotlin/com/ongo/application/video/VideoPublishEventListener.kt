package com.ongo.application.video

import com.ongo.application.config.ExecutorConfig
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.domain.lock.DistributedLockPort
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.LocalDateTime
import java.util.UUID

@Component
class VideoPublishEventListener(
    private val platformUploadServices: List<PlatformUploadService>,
    private val videoUploadRepository: VideoUploadRepository,
    private val videoRepository: VideoRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val storageService: StorageService? = null,
    private val distributedLockPort: DistributedLockPort? = null,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    // PublishVideoUseCase는 트랜잭션 커밋 뒤에 처리하고, 예약 디스패처는
    // 스케줄러 스레드에서 이미 claim을 커밋한 뒤 발행한다. 후자는 활성 트랜잭션이
    // 없으므로 fallbackExecution 없이는 이벤트가 조용히 버려진다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun handleVideoPublish(event: VideoPublishEvent) {
        log.info("영상 게시 이벤트 수신: videoId={}, platforms={}", event.videoId,
            event.platformConfigs.map { it.platform })

        // Virtual Thread 기반 병렬 업로드 (Semaphore로 동시 실행 제한)
        val eventLeaseOwner = "publish:${event.videoId}:${UUID.randomUUID()}"
        ExecutorConfig.newVirtualExecutor().use { executor ->
            val futures = event.platformConfigs.map { config ->
                executor.submit<Unit> {
                    ExecutorConfig.uploadSemaphore.acquire()
                    val platformSemaphore = ExecutorConfig.platformUploadSemaphore(config.platform)
                    platformSemaphore.acquire()
                    try {
                        val ran = if (distributedLockPort == null) {
                            uploadToPlatform(event, config, config.leaseOwner ?: eventLeaseOwner)
                            true
                        } else {
                            distributedLockPort.withAnyLock(
                                ExecutorConfig.platformUploadLockIds(config.platform),
                            ) {
                                uploadToPlatform(event, config, config.leaseOwner ?: eventLeaseOwner)
                                true
                            }
                        }
                        if (ran != true) {
                            log.warn("플랫폼 {} 분산 동시성 슬롯을 확보하지 못해 업로드를 보류합니다: videoId={}", config.platform, event.videoId)
                        }
                    } finally {
                        platformSemaphore.release()
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

    private fun uploadToPlatform(event: VideoPublishEvent, config: PlatformUploadConfig, leaseOwner: String) {
        if (config.scheduledAt?.isAfter(LocalDateTime.now()) == true) {
            log.debug("예약 시각 전이라 외부 게시를 보류합니다: videoId={}, platform={}, scheduledAt={}", event.videoId, config.platform, config.scheduledAt)
            return
        }
        val service = platformUploadServices.find { it.supports(config.platform) }
        if (service == null) {
            /*
             * 게시 게이트가 생기기 전에 만들어진 행이 여기까지 온다. 이유를 capability 에서
             * 가져와 사용자가 읽을 수 있는 문장으로 남긴다 — "지원되지 않는 플랫폼" 만으로는
             * 왜 안 되는지, 기다리면 되는지 알 수 없다.
             */
            val reason = PlatformUploadCapabilities.unsupportedReason(config.platform)
            log.error("플랫폼 {} 에 대한 업로드 서비스를 찾을 수 없습니다: {}", config.platform, reason)
            updateUploadStatus(
                config.videoUploadId,
                UploadStatus.FAILED,
                reason,
            )
            fireCompletedEvent(event, config.platform, false, errorMessage = reason)
            return
        }

        val claimed = if (config.leaseOwner == null) {
            videoUploadRepository.claim(
                id = config.videoUploadId,
                owner = leaseOwner,
                now = LocalDateTime.now(),
                leaseUntil = LocalDateTime.now().plusMinutes(30),
            )
        } else {
            // 예약 디스패처가 이미 원자적으로 확보한 lease를 이어서 사용한다.
            // 소유자와 만료를 확인해 오래된/변조된 이벤트는 외부 호출하지 않는다.
            videoUploadRepository.findById(config.videoUploadId)?.takeIf {
                it.leaseOwner == leaseOwner && it.leaseUntil?.isAfter(LocalDateTime.now()) == true
            }
        }
        if (claimed == null) {
            log.warn("플랫폼 {} 업로드 lease 확인 실패, 중복 작업을 건너뜁니다: videoUploadId={}", config.platform, config.videoUploadId)
            return
        }

        // 예약 취소와 dispatcher가 동시에 경합할 수 있다. 취소가 lease를
        // 회수한 뒤에는 외부 HTTP 호출을 시작하지 않는다.
        if (videoUploadRepository.findById(config.videoUploadId)?.status == UploadStatus.CANCELLED) {
            log.info("취소된 업로드는 외부 게시를 시작하지 않습니다: videoUploadId={}", config.videoUploadId)
            return
        }

        val fileUrl = try {
            storageService?.getFileUrl(event.videoId, event.fileUrl) ?: event.fileUrl
        } catch (e: Exception) {
            // Storage URL resolution happens before any provider request. It is a
            // local/preparation failure, so marking it UNCONFIRMED would make a
            // user investigate a publication that was never attempted.
            val message = "게시 파일 준비 실패: ${e.message ?: "파일 URL을 확인하지 못했습니다."}"
            updateUploadStatus(config.videoUploadId, UploadStatus.FAILED, message, leaseOwner = leaseOwner)
            fireCompletedEvent(event, config.platform, false, errorMessage = message)
            log.error("영상 {} 파일 URL 준비 실패: platform={}", event.videoId, config.platform, e)
            return
        }
        if (fileUrl == null) {
            log.warn("영상 {} 에 fileUrl이 없어 플랫폼 업로드를 건너뜁니다 (스트리밍 업로드)", event.videoId)
            val message = "파일 URL이 없습니다. 스트리밍 방식으로 업로드된 영상입니다."
            updateUploadStatus(config.videoUploadId, UploadStatus.FAILED, message, leaseOwner = leaseOwner)
            fireCompletedEvent(event, config.platform, false, errorMessage = message)
            return
        }

        val result = try {
            log.info("플랫폼 {} 업로드 시작: videoId={}", config.platform, event.videoId)
            // This is the external provider boundary. Only exceptions from this
            // call may become UNCONFIRMED because the provider may have accepted
            // bytes before the response was lost.
            service.upload(config, fileUrl, event.userId)
        } catch (e: Exception) {
            log.error("플랫폼 {} 업로드 중 예외 발생: videoId={}", config.platform, event.videoId, e)
            // 외부 플랫폼 호출은 타임아웃 시 이미 게시가 완료됐을 가능성이 있다.
            // 무조건 FAILED로 기록하면 사용자가 재시도하여 중복 게시를 만들 수 있으므로
            // 확인 불가 상태로 남기고, 후속 조회/운영 재검증 대상으로 보낸다.
            updateUploadStatus(config.videoUploadId, UploadStatus.UNCONFIRMED, e.message, leaseOwner = leaseOwner)
            fireCompletedEvent(event, config.platform, false, errorMessage = "게시 결과 확인 필요: ${e.message}")
            return
        }

        when (val outcome = result.toPublishOutcome()) {
            is PublishOutcome.Published -> {
                updateUploadStatus(
                    config.videoUploadId,
                    UploadStatus.PUBLISHED,
                    platformVideoId = outcome.platformVideoId,
                    platformUrl = outcome.platformUrl,
                    clearPollToken = true,
                    leaseOwner = leaseOwner,
                )
                fireCompletedEvent(
                    event,
                    config.platform,
                    true,
                    platformUrl = outcome.platformUrl,
                    platformPostId = outcome.platformVideoId,
                )
                log.info("플랫폼 {} 업로드 성공: videoId={}, platformUrl={}", config.platform, event.videoId, outcome.platformUrl)
            }
            is PublishOutcome.Accepted -> {
                updateUploadStatus(
                    config.videoUploadId,
                    UploadStatus.PROCESSING,
                    platformVideoId = outcome.platformVideoId,
                    platformUrl = result.platformUrl,
                    pollToken = outcome.pollToken,
                    nextRetryAt = LocalDateTime.now().plus(outcome.retryAfter),
                    leaseOwner = leaseOwner,
                )
                log.info("플랫폼 {} 업로드 수락: videoId={}, 후속 상태 확인 예약", config.platform, event.videoId)
            }
            is PublishOutcome.Failed -> {
                val current = videoUploadRepository.findById(config.videoUploadId)
                val retryScheduled = outcome.retryable &&
                    outcome.retryAfter != null &&
                    current != null &&
                    current.attemptCount < MAX_DURABLE_UPLOAD_ATTEMPTS
                if (retryScheduled) {
                    updateUploadStatus(
                        config.videoUploadId,
                        UploadStatus.UPLOADING,
                        outcome.message,
                        nextRetryAt = LocalDateTime.now().plus(outcome.retryAfter!!),
                        clearPollToken = true,
                        leaseOwner = leaseOwner,
                    )
                    log.warn(
                        "플랫폼 {} 일시 오류를 durable 재시도로 예약합니다: videoId={}, attempt={}, nextRetryAt={}",
                        config.platform,
                        event.videoId,
                        current.attemptCount,
                        LocalDateTime.now().plus(outcome.retryAfter),
                    )
                } else {
                    updateUploadStatus(config.videoUploadId, UploadStatus.FAILED, outcome.message, clearPollToken = true, leaseOwner = leaseOwner)
                    fireCompletedEvent(event, config.platform, false, errorMessage = outcome.message)
                    log.warn("플랫폼 {} 업로드 실패: videoId={}, error={}", config.platform, event.videoId, outcome.message)
                }
            }
            is PublishOutcome.Unconfirmed -> {
                updateUploadStatus(
                    config.videoUploadId,
                    UploadStatus.UNCONFIRMED,
                    outcome.message,
                    platformVideoId = outcome.platformVideoId,
                    pollToken = outcome.pollToken,
                    leaseOwner = leaseOwner,
                )
                fireCompletedEvent(
                    event,
                    config.platform,
                    false,
                    platformPostId = outcome.platformVideoId,
                    errorMessage = outcome.message,
                )
                log.warn("플랫폼 {} 게시 결과 확인 필요: videoId={}, error={}", config.platform, event.videoId, outcome.message)
            }
        }
    }

    private fun updateUploadStatus(
        uploadId: Long,
        status: UploadStatus,
        errorMessage: String? = null,
        platformVideoId: String? = null,
        platformUrl: String? = null,
        pollToken: String? = null,
        nextRetryAt: LocalDateTime? = null,
        clearPollToken: Boolean = false,
        leaseOwner: String? = null,
    ) {
        val upload = videoUploadRepository.findById(uploadId) ?: return
        val updated = upload.copy(
                status = status,
                errorMessage = errorMessage,
                platformVideoId = platformVideoId ?: upload.platformVideoId,
                platformUrl = platformUrl ?: upload.platformUrl,
                pollToken = if (clearPollToken) null else pollToken ?: upload.pollToken,
                leaseOwner = null,
                leaseUntil = null,
                nextRetryAt = nextRetryAt,
                lastError = errorMessage,
                publishedAt = if (status == UploadStatus.PUBLISHED) LocalDateTime.now() else upload.publishedAt,
            )
        if (leaseOwner == null) {
            videoUploadRepository.update(updated)
        } else if (!videoUploadRepository.updateOwned(updated, leaseOwner)) {
            log.warn("lease를 잃은 작업자의 결과 반영을 차단했습니다: uploadId={}, owner={}", uploadId, leaseOwner)
        }
    }

    private fun updateOverallVideoStatus(videoId: Long) {
        val uploads = videoUploadRepository.findByVideoId(videoId)
        if (uploads.isEmpty()) return

        val video = videoRepository.findById(videoId) ?: return

        val overallStatus = when {
            uploads.all { it.status == UploadStatus.PUBLISHED } -> UploadStatus.PUBLISHED
            uploads.any { it.status == UploadStatus.PUBLISHED } && uploads.any {
                it.status == UploadStatus.FAILED || it.status == UploadStatus.REJECTED ||
                    it.status == UploadStatus.UNCONFIRMED || it.status == UploadStatus.CANCELLED
            } -> UploadStatus.PARTIALLY_PUBLISHED
            uploads.all { it.status == UploadStatus.CANCELLED } -> UploadStatus.DRAFT
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
        platformPostId: String? = null,
        errorMessage: String? = null,
    ) {
        eventPublisher.publishEvent(
            UploadCompletedEvent(
                videoId = event.videoId,
                userId = event.userId,
                platform = platform,
                success = success,
                platformUrl = platformUrl,
                platformPostId = platformPostId,
                errorMessage = errorMessage,
                videoUploadId = event.platformConfigs.firstOrNull { it.platform == platform }?.videoUploadId,
            )
        )
    }

    companion object {
        /** 한 번의 요청 안에서 재시도한 뒤에도 지속 오류면 운영자/사용자 재시도로 넘긴다. */
        private const val MAX_DURABLE_UPLOAD_ATTEMPTS = 5
    }
}
