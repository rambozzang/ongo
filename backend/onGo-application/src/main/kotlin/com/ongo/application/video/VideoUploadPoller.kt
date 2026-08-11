package com.ongo.application.video

import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.Platform
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

/**
 * 플랫폼이 수락했지만 아직 공개하지 않은 게시물을 polling한다.
 *
 * 모든 상태 변경은 DB lease를 가진 작업자만 반영하므로 여러 API 인스턴스가
 * 동시에 떠 있거나 배포 중 재시작되어도 동일 게시물을 재전송하지 않는다.
 */
@Component
class VideoUploadPoller(
    private val platformUploadServices: List<PlatformUploadService>,
    private val videoUploadRepository: VideoUploadRepository,
    private val videoRepository: VideoRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${video.publish.poll-delay-ms:15000}")
    fun pollDueUploads() {
        val now = LocalDateTime.now()
        videoUploadRepository.findDueProcessingUploads(now).forEach { poll(it, now) }
    }

    /**
     * 외부 호출 결과를 잃어버린 작업을 사용자가 명시적으로 재확인한다.
     *
     * 이 경로는 새 업로드를 시작하지 않고 플랫폼의 상태 조회 API만 호출한다.
     * platformVideoId/pollToken이 없는 작업은 중복 게시를 피하기 위해 재전송하지 않고
     * 명시적으로 실패시킨다.
     */
    fun recheck(userId: Long, videoId: Long, platform: Platform) {
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        val upload = videoUploadRepository.findByVideoIdAndPlatform(videoId, platform)
            ?: throw NotFoundException("업로드 기록", "$videoId/${platform.name}")
        if (upload.status != UploadStatus.UNCONFIRMED) {
            throw IllegalStateException("게시 결과 확인이 필요한 작업만 재확인할 수 있습니다. 현재 상태: ${upload.status}")
        }
        val token = upload.pollToken ?: upload.platformVideoId
            ?: throw IllegalStateException("플랫폼에서 조회할 게시 식별자가 없습니다. 중복 게시를 막기 위해 자동 재전송하지 않습니다.")
        poll(upload, LocalDateTime.now(), token)
    }

    /** Same-provider accounts must be rechecked by their durable upload row. */
    fun recheckUpload(userId: Long, videoId: Long, uploadId: Long) {
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        val upload = videoUploadRepository.findById(uploadId)
            ?.takeIf { it.videoId == videoId }
            ?: throw NotFoundException("업로드 기록", "$videoId/$uploadId")
        if (upload.status != UploadStatus.UNCONFIRMED) {
            throw IllegalStateException("게시 결과 확인이 필요한 작업만 재확인할 수 있습니다. 현재 상태: ${upload.status}")
        }
        val token = upload.pollToken ?: upload.platformVideoId
            ?: throw IllegalStateException("플랫폼에서 조회할 게시 식별자가 없습니다. 중복 게시를 막기 위해 자동 재전송하지 않습니다.")
        poll(upload, LocalDateTime.now(), token)
    }

    private fun poll(upload: VideoUpload, now: LocalDateTime, tokenOverride: String? = null) {
        val uploadId = upload.id ?: return
        val video = videoRepository.findById(upload.videoId) ?: return
        val service = platformUploadServices.find { it.supports(upload.platform) }
        if (service == null) {
            finish(upload, video.userId, UploadStatus.FAILED, "지원되지 않는 플랫폼: ${upload.platform}", null, null)
            return
        }

        val owner = "poll:${uploadId}:${UUID.randomUUID()}"
        val claimed = videoUploadRepository.claimForStatusCheck(
            id = uploadId,
            owner = owner,
            now = now,
            leaseUntil = now.plusMinutes(5),
        ) ?: return

        try {
            val pollToken = claimed.pollToken ?: tokenOverride
                ?: throw IllegalStateException("플랫폼 상태 조회 토큰이 없습니다.")
            val result = service.poll(
                upload.platform,
                pollToken,
                video.userId,
                claimed.platformUrl,
                claimed.channelId,
            )
            when (val outcome = result.toPublishOutcome()) {
                is PublishOutcome.Published -> finish(
                    claimed,
                    video.userId,
                    UploadStatus.PUBLISHED,
                    null,
                    outcome,
                    owner,
                )
                is PublishOutcome.Accepted -> updateOwned(
                    claimed,
                    owner,
                    status = UploadStatus.PROCESSING,
                    errorMessage = null,
                    platformVideoId = outcome.platformVideoId,
                    platformUrl = result.platformUrl,
                    pollToken = outcome.pollToken,
                    nextRetryAt = now.plus(outcome.retryAfter),
                )
                is PublishOutcome.Failed -> finish(
                    claimed,
                    video.userId,
                    UploadStatus.FAILED,
                    outcome.message,
                    null,
                    owner,
                )
                is PublishOutcome.Unconfirmed -> finish(
                    claimed,
                    video.userId,
                    UploadStatus.UNCONFIRMED,
                    outcome.message,
                    null,
                    owner,
                )
            }
        } catch (e: Exception) {
            val message = e.message ?: "플랫폼 상태 확인 중 오류가 발생했습니다."
            if (tokenOverride != null || claimed.attemptCount >= MAX_POLL_ATTEMPTS) {
                finish(claimed, video.userId, UploadStatus.UNCONFIRMED, "게시 결과 확인 실패: $message", null, owner)
            } else {
                updateOwned(
                    claimed,
                    owner,
                    status = UploadStatus.PROCESSING,
                    errorMessage = "상태 확인 재시도 예정: $message",
                    platformVideoId = claimed.platformVideoId,
                    platformUrl = claimed.platformUrl,
                    pollToken = claimed.pollToken,
                    nextRetryAt = now.plusSeconds(POLL_RETRY_SECONDS),
                )
            }
            log.warn("플랫폼 {} 게시 상태 확인 실패 (시도 {}): {}", upload.platform, claimed.attemptCount, message)
        }
    }

    private fun finish(
        upload: VideoUpload,
        userId: Long,
        status: UploadStatus,
        errorMessage: String?,
        published: PublishOutcome.Published?,
        owner: String? = null,
    ) {
        updateOwned(
            upload,
            owner,
            status = status,
            errorMessage = errorMessage,
            platformVideoId = published?.platformVideoId ?: upload.platformVideoId,
            platformUrl = published?.platformUrl ?: upload.platformUrl,
            pollToken = null,
            nextRetryAt = null,
            clearPollToken = true,
        )
        updateOverallVideoStatus(upload.videoId)
        if (status == UploadStatus.PUBLISHED || status == UploadStatus.FAILED || status == UploadStatus.UNCONFIRMED) {
            eventPublisher.publishEvent(
                UploadCompletedEvent(
                    videoId = upload.videoId,
                    userId = userId,
                    platform = upload.platform,
                    success = status == UploadStatus.PUBLISHED,
                    platformUrl = published?.platformUrl,
                    platformPostId = published?.platformVideoId ?: upload.platformVideoId,
                    errorMessage = errorMessage,
                    videoUploadId = upload.id,
                )
            )
        }
    }

    private fun updateOwned(
        upload: VideoUpload,
        owner: String?,
        status: UploadStatus,
        errorMessage: String?,
        platformVideoId: String?,
        platformUrl: String?,
        pollToken: String?,
        nextRetryAt: LocalDateTime?,
        clearPollToken: Boolean = false,
    ) {
        val updated = upload.copy(
            status = status,
            errorMessage = errorMessage,
            platformVideoId = platformVideoId,
            platformUrl = platformUrl,
            pollToken = if (clearPollToken) null else pollToken,
            nextRetryAt = nextRetryAt,
            leaseOwner = null,
            leaseUntil = null,
            lastError = errorMessage,
            publishedAt = if (status == UploadStatus.PUBLISHED) LocalDateTime.now() else upload.publishedAt,
        )
        val changed = if (owner == null) {
            videoUploadRepository.update(updated)
            true
        } else {
            videoUploadRepository.updateOwned(updated, owner)
        }
        if (changed) updateOverallVideoStatus(upload.videoId)
    }

    private fun updateOverallVideoStatus(videoId: Long) {
        val uploads = videoUploadRepository.findByVideoId(videoId)
        val video = videoRepository.findById(videoId) ?: return
        val status = when {
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
        videoRepository.update(video.copy(status = status))
    }

    companion object {
        private const val MAX_POLL_ATTEMPTS = 12
        private const val POLL_RETRY_SECONDS = 60L
    }
}
