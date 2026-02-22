package com.ongo.application.video

import com.ongo.domain.event.VideoResizeRequestedEvent
import com.ongo.domain.video.VideoResizeRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.nio.file.Files
import java.nio.file.Path

@Component
class VideoResizeEventListener(
    private val resizeRepository: VideoResizeRepository,
    private val transcodeService: VideoTranscodeService,
    private val storageService: StorageService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleResize(event: VideoResizeRequestedEvent) {
        log.info("영상 리사이즈 시작: resizeId={}, videoId={}", event.resizeId, event.videoId)

        val resize = resizeRepository.findById(event.resizeId) ?: return
        resizeRepository.updateStatus(event.resizeId, "PROCESSING")

        val tempDir = Path.of("/tmp/ongo-resize/${event.resizeId}")
        Files.createDirectories(tempDir)
        val inputFile = tempDir.resolve("original.mp4")
        val outputFile = tempDir.resolve("resized_${resize.aspectRatio.replace(":", "x")}.mp4")

        try {
            // 1. 원본 다운로드
            storageService.downloadFile(event.videoId).use { input ->
                Files.copy(input, inputFile)
            }

            // 2. FFmpeg 리사이즈
            val spec = VideoTranscodeService.RESIZE_SPECS[resize.aspectRatio]
                ?: throw RuntimeException("Unknown ratio: ${resize.aspectRatio}")
            transcodeService.resize(inputFile.toString(), outputFile.toString(), spec)

            // 3. 결과 업로드
            val fileSize = Files.size(outputFile)
            val uploadKey = "videos/${event.videoId}/resized/${resize.aspectRatio.replace(":", "x")}.mp4"
            val url = Files.newInputStream(outputFile).use { inputStream ->
                storageService.uploadFile(uploadKey, inputStream, "video/mp4", fileSize)
            }

            resizeRepository.updateStatus(event.resizeId, "COMPLETED", url, fileSize)
            log.info("영상 리사이즈 완료: resizeId={}, size={}bytes", event.resizeId, fileSize)
        } catch (e: Exception) {
            log.error("영상 리사이즈 실패: resizeId={}", event.resizeId, e)
            resizeRepository.updateStatus(event.resizeId, "FAILED", errorMessage = e.message?.take(500))
        } finally {
            try {
                Files.deleteIfExists(outputFile)
                Files.deleteIfExists(inputFile)
                Files.deleteIfExists(tempDir)
            } catch (e: Exception) {
                log.debug("임시 파일 정리: {}", e.message)
            }
        }
    }
}
