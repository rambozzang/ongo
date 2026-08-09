package com.ongo.application.video

import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.domain.lock.DistributedLockPort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 작업자가 죽거나 배포로 중단된 외부 호출을 회수한다.
 *
 * 결과를 알 수 없는 외부 호출은 재전송하지 않는다. 자동 재전송은 중복 게시를
 * 만들 수 있으므로 UNCONFIRMED로 노출하고, 명시적 재검증/재시도 흐름으로 보낸다.
 */
@Component
class VideoUploadLeaseReaper(
    private val videoUploadRepository: VideoUploadRepository,
    private val videoRepository: VideoRepository,
    private val distributedLockPort: DistributedLockPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${upload.lease.reaper-delay-ms:60000}")
    fun recoverExpiredLeases() {
        // 여러 API 인스턴스가 같은 만료 작업을 훑지 않도록 스케줄러 자체도
        // 분산 락으로 직렬화한다. 행 조건부 갱신이 최종 안전망이지만, 락은
        // 불필요한 조회·상태 재계산과 운영 로그 중복을 줄인다.
        distributedLockPort.withLock(LEASE_REAPER_LOCK_ID) {
            recoverExpiredLeasesOnce()
        }
    }

    private fun recoverExpiredLeasesOnce() {
        val recovered = videoUploadRepository.recoverExpiredLeases(LocalDateTime.now())
        if (recovered.isEmpty()) return

        recovered.groupBy { it.videoId }.forEach { (videoId, _) ->
            updateOverallVideoStatus(videoId)
        }
        val polling = recovered.count { it.status == UploadStatus.PROCESSING }
        val unconfirmed = recovered.size - polling
        log.warn(
            "외부 게시 lease 만료 작업 {}건을 회수했습니다 (polling 재개={}, 결과 확인 필요={})",
            recovered.size,
            polling,
            unconfirmed,
        )
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
            uploads.all { it.status == UploadStatus.UNCONFIRMED } -> UploadStatus.UNCONFIRMED
            uploads.all { it.status == UploadStatus.FAILED || it.status == UploadStatus.REJECTED } -> UploadStatus.FAILED
            uploads.any { it.status == UploadStatus.PROCESSING || it.status == UploadStatus.REVIEW } -> UploadStatus.PROCESSING
            else -> UploadStatus.UPLOADING
        }
        videoRepository.update(video.copy(status = status))
    }

    companion object {
        private val LEASE_REAPER_LOCK_ID = "ongo.video-upload-lease-reaper".hashCode().toLong()
    }
}
