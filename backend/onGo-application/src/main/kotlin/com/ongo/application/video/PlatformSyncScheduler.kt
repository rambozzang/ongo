package com.ongo.application.video

import com.ongo.common.enums.UploadStatus
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.video.VideoPlatformMetaRepository
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 플랫폼에서 변경된 메타데이터를 주기적으로 onGo DB에 동기���.
 * 각 플랫폼 API에서 제목, 설명 등을 가져와 VideoPlatformMeta에 반영.
 */
@Component
class PlatformSyncScheduler(
    private val videoUploadRepository: VideoUploadRepository,
    private val videoPlatformMetaRepository: VideoPlatformMetaRepository,
    private val channelRepository: ChannelRepository,
    private val platformClientPort: PlatformClientPort,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 30�� 마��� PUBLISHED 상태 업로드의 메타데이터를 플랫폼에서 동기화 */
    @Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 60 * 1000)
    fun syncPublishedVideos() {
        log.info("플랫폼 메타데이터 동기화 시작")

        val allChannels = channelRepository.findAllActive()
        if (allChannels.isEmpty()) {
            log.debug("활성 채널 없음 — 동기화 건너뜀")
            return
        }

        // 채널별로 그룹화: (userId, platform) → channel
        val channelMap = allChannels.associateBy { it.userId to it.platform }

        // PUBLISHED 상태인 모��� 업로드 조회
        val publishedUploads = videoUploadRepository.findPendingUploads()
            .filter { false } // findPendingUploads는 다른 용도 — 별��� 메서드 필요

        // 대신 채널별로 순회하며 해당 사용자의 업로드 조회
        var syncCount = 0
        for (channel in allChannels) {
            val uploads = videoUploadRepository.findByPlatformAndUserId(channel.platform, channel.userId)
                .filter { it.status == UploadStatus.PUBLISHED && it.platformVideoId != null }

            for (upload in uploads) {
                try {
                    val remoteMeta = platformClientPort.getVideoMetadata(
                        platform = upload.platform,
                        platformVideoId = upload.platformVideoId!!,
                        accessToken = channel.accessToken,
                    ) ?: continue

                    // 로컬 메타 조회 및 업데이트
                    val localMeta = videoPlatformMetaRepository.findByVideoUploadId(upload.id!!)
                    if (localMeta != null) {
                        val updated = localMeta.copy(
                            title = remoteMeta.title,
                            description = remoteMeta.description,
                            tags = remoteMeta.tags.ifEmpty { localMeta.tags },
                        )
                        if (updated != localMeta) {
                            videoPlatformMetaRepository.update(updated)
                            syncCount++
                        }
                    }
                } catch (e: Exception) {
                    log.warn("플랫폼 {} 메타 동기화 실패: uploadId={}, error={}", upload.platform, upload.id, e.message)
                }
            }
        }

        log.info("플랫폼 메타데이터 동기화 완료: {}건 업데이트", syncCount)
    }
}
