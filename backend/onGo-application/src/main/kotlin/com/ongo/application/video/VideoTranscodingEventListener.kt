package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.VariantStatus
import com.ongo.domain.video.PlatformVideoSpec
import com.ongo.domain.video.VideoVariant
import com.ongo.domain.video.VideoVariantRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.concurrent.Executors

@Component
class VideoTranscodingEventListener(
    private val videoVariantRepository: VideoVariantRepository,
    private val transcodingService: TranscodingService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleTranscoding(event: VideoTranscodingEvent) {
        log.info(
            "트랜스코딩 이벤트 수신: videoId={}, platforms={}",
            event.videoId, event.platforms,
        )

        // 각 플랫폼별 variant 레코드 생성 (PENDING)
        val variants = event.platforms.map { platform ->
            val existing = videoVariantRepository.findByVideoIdAndPlatform(event.videoId, platform)
            if (existing != null) {
                // 기존 레코드가 있으면 PENDING으로 리셋
                videoVariantRepository.update(
                    existing.copy(
                        status = VariantStatus.PENDING,
                        errorMessage = null,
                        fileUrl = null,
                        fileSizeBytes = null,
                    )
                )
            } else {
                videoVariantRepository.save(
                    VideoVariant(
                        videoId = event.videoId,
                        platform = platform,
                        status = VariantStatus.PENDING,
                    )
                )
            }
        }

        // Virtual Thread 기반 병렬 트랜스코딩
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val futures = variants.map { variant ->
                executor.submit<Unit> {
                    transcodePlatform(event, variant)
                }
            }

            futures.forEach { future ->
                try {
                    future.get()
                } catch (e: Exception) {
                    log.error("트랜스코딩 작업 실행 중 오류", e)
                }
            }
        }

        log.info("트랜스코딩 완료: videoId={}", event.videoId)
    }

    private fun transcodePlatform(event: VideoTranscodingEvent, variant: VideoVariant) {
        val platform = variant.platform
        val variantId = variant.id!!
        val spec = PlatformVideoSpec.forPlatform(platform)

        try {
            // PROCESSING 상태로 업데이트
            videoVariantRepository.update(
                variant.copy(status = VariantStatus.PROCESSING)
            )

            log.info("트랜스코딩 시작: videoId={}, platform={}, spec={}x{}", event.videoId, platform, spec.width, spec.height)

            // ffmpeg 트랜스코딩 실행
            val result = transcodingService.transcode(event.fileUrl, event.videoId, platform, spec)

            // COMPLETED 상태로 업데이트
            val updated = videoVariantRepository.findById(variantId)!!
            videoVariantRepository.update(
                updated.copy(
                    status = VariantStatus.COMPLETED,
                    fileUrl = result.fileUrl,
                    fileSizeBytes = result.fileSizeBytes,
                    width = result.width,
                    height = result.height,
                    bitrateKbps = spec.bitrateKbps,
                )
            )

            log.info(
                "트랜스코딩 성공: videoId={}, platform={}, fileSize={}",
                event.videoId, platform, result.fileSizeBytes,
            )
        } catch (e: Exception) {
            log.error("트랜스코딩 실패: videoId={}, platform={}", event.videoId, platform, e)

            try {
                val current = videoVariantRepository.findById(variantId)
                if (current != null) {
                    videoVariantRepository.update(
                        current.copy(
                            status = VariantStatus.FAILED,
                            errorMessage = e.message?.take(500),
                        )
                    )
                }
            } catch (updateEx: Exception) {
                log.error("트랜스코딩 실패 상태 업데이트 중 오류", updateEx)
            }
        }
    }
}
