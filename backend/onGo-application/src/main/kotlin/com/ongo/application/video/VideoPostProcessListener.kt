package com.ongo.application.video

import com.ongo.domain.video.VideoMediaInfoRepository
import com.ongo.domain.video.VideoProcessingProgressRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.entity.ProcessingStage
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.nio.file.Files
import java.nio.file.Path

@Component
class VideoPostProcessListener(
    private val ffprobeService: MediaProbeService,
    private val thumbnailService: ThumbnailService,
    private val storageService: StorageService,
    private val videoRepository: VideoRepository,
    private val mediaInfoRepository: VideoMediaInfoRepository,
    private val progressRepository: VideoProcessingProgressRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePostProcess(event: VideoPostProcessEvent) {
        log.info("영상 후처리 시작: videoId={}", event.videoId)

        val tempDir = Path.of("/tmp/ongo-process/${event.videoId}")
        Files.createDirectories(tempDir)
        val inputFile = tempDir.resolve("original.mp4")

        try {
            // 원본 1회 다운로드 — probe와 thumbnail이 공유 (SDK 인증 사용)
            storageService.downloadFile(event.videoId).use { input ->
                Files.copy(input, inputFile)
            }
        } catch (e: Exception) {
            log.error("원본 다운로드 실패: videoId={}", event.videoId, e)
            return
        }

        try {
            // 1. FFprobe analysis
            var durationMs: Long? = null
            try {
                progressRepository.upsertProgress(event.videoId, ProcessingStage.PROBE, null, 0, "분석 시작")
                val info = ffprobeService.probe(inputFile.toString(), event.videoId)
                val saved = mediaInfoRepository.save(info)
                durationMs = saved.durationMs
                progressRepository.upsertProgress(event.videoId, ProcessingStage.PROBE, null, 100, "분석 완료")
                log.info("FFprobe 완료: videoId={}, codec={}, {}x{}", event.videoId, saved.videoCodec, saved.width, saved.height)
                eventPublisher.publishEvent(VideoProbeCompletedEvent(event.videoId, saved))
            } catch (e: Exception) {
                log.warn("FFprobe 실패: videoId={}", event.videoId, e)
                progressRepository.upsertProgress(event.videoId, ProcessingStage.PROBE, null, 100, "분석 실패")
            }

            // 2. Thumbnail generation — 같은 로컬 파일 재사용 (추가 다운로드 없음)
            try {
                progressRepository.upsertProgress(event.videoId, ProcessingStage.THUMBNAIL, null, 0, "썸네일 생성 중")
                val thumbnailUrls = thumbnailService.generateThumbnails(
                    inputFilePath = inputFile.toString(),
                    videoId = event.videoId,
                    durationMs = durationMs,
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

            // 3. Caption generation — 사용자 요청 시에만 생성 (CaptionUseCase.generateCaption)
        } finally {
            // 모든 작업 완료 후 임시 파일 정리
            try {
                Files.deleteIfExists(inputFile)
                Files.deleteIfExists(tempDir)
            } catch (e: Exception) {
                log.debug("임시 파일 정리: {}", e.message)
            }
        }

        log.info("영상 후처리 완료: videoId={}", event.videoId)
    }
}
