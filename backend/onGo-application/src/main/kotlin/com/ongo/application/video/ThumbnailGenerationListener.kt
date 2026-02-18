package com.ongo.application.video

import com.ongo.domain.video.VideoProcessingProgressRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.entity.ProcessingStage
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ThumbnailGenerationListener(
    private val thumbnailService: ThumbnailService,
    private val videoRepository: VideoRepository,
    private val progressRepository: VideoProcessingProgressRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun handleThumbnailGeneration(event: ThumbnailGenerationEvent) {
        log.info("썸네일 생성 시작: videoId={}", event.videoId)

        try {
            progressRepository.upsertProgress(event.videoId, ProcessingStage.THUMBNAIL, null, 0, "썸네일 생성 중")

            val thumbnailUrls = thumbnailService.generateThumbnails(
                inputFilePath = event.inputFilePath,
                videoId = event.videoId,
                durationMs = event.durationMs,
            )

            if (thumbnailUrls.isNotEmpty()) {
                val video = videoRepository.findById(event.videoId)
                if (video != null) {
                    videoRepository.update(
                        video.copy(
                            autoThumbnails = thumbnailUrls,
                            selectedThumbnailIndex = 0,
                        )
                    )
                }
            }

            progressRepository.upsertProgress(event.videoId, ProcessingStage.THUMBNAIL, null, 100, "썸네일 ${thumbnailUrls.size}개 생성 완료")
            log.info("썸네일 생성 완료: videoId={}, count={}", event.videoId, thumbnailUrls.size)
        } catch (e: Exception) {
            log.error("썸네일 생성 실패: videoId={}", event.videoId, e)
            progressRepository.upsertProgress(event.videoId, ProcessingStage.THUMBNAIL, null, 0, "실패: ${e.message?.take(200)}")
        }
    }
}
