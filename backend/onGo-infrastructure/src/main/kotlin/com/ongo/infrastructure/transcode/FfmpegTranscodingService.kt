package com.ongo.infrastructure.transcode

import com.ongo.application.video.TranscodeResult
import com.ongo.application.video.TranscodingService
import com.ongo.common.enums.Platform
import com.ongo.domain.video.PlatformVideoSpec
import com.ongo.infrastructure.external.storage.StorageClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * FFmpeg 라이브러리를 사용하여 비디오 트랜스코딩을 수행하는 서비스 구현체
 */
@Service
class FfmpegTranscodingService(
    private val storageClient: StorageClient,
) : TranscodingService {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 지정된 소스 영상을 플랫폼별 스펙에 맞춰 트랜스코딩합니다.
     * 과정: 원본 다운로드 -> FFmpeg 실행 -> 결과를 Storage에 업로드 -> 임시 파일 삭제
     */
    override fun transcode(
        inputUrl: String,
        videoId: Long,
        platform: Platform,
        spec: PlatformVideoSpec,
    ): TranscodeResult {
        val tempDir = Path.of("/tmp/ongo-transcode/$videoId/${platform.name.lowercase()}")
        Files.createDirectories(tempDir)

        val inputFile = tempDir.resolve("input.mp4")
        val outputFile = tempDir.resolve("output.mp4")

        try {
            // 1. 원본 다운로드
            log.info("원본 다운로드 시작: videoId={}, url={}", videoId, inputUrl)
            downloadFile(inputUrl, inputFile)

            // 2. ffmpeg 트랜스코딩
            log.info(
                "ffmpeg 실행: videoId={}, platform={}, target={}x{} @{}kbps",
                videoId, platform, spec.width, spec.height, spec.bitrateKbps,
            )
            runFfmpeg(inputFile, outputFile, spec)

            // 3. 변환된 파일을 Storage에 업로드
            val storageKey = "videos/$videoId/variants/${platform.name.lowercase()}/video.mp4"
            val fileSize = Files.size(outputFile)
            val fileUrl = Files.newInputStream(outputFile).use { inputStream ->
                storageClient.uploadFile(storageKey, inputStream, "video/mp4", fileSize)
            }

            log.info("변환 파일 업로드 완료: videoId={}, platform={}, key={}", videoId, platform, storageKey)

            return TranscodeResult(
                fileUrl = fileUrl,
                fileSizeBytes = fileSize,
                width = spec.width,
                height = spec.height,
            )
        } finally {
            // 4. 임시 파일 정리
            try {
                Files.deleteIfExists(outputFile)
                Files.deleteIfExists(inputFile)
                Files.deleteIfExists(tempDir)
                Files.deleteIfExists(tempDir.parent)
            } catch (e: Exception) {
                log.warn("임시 파일 정리 실패: {}", tempDir, e)
            }
        }
    }

    private fun downloadFile(url: String, target: Path) {
        URI(url).toURL().openStream().use { input ->
            Files.copy(input, target)
        }
    }

    /**
     * 실제 FFmpeg 프로세스를 실행하여 비디오 변환을 수행합니다.
     */
    private fun runFfmpeg(input: Path, output: Path, spec: PlatformVideoSpec) {
        val scaleFilter = "scale=${spec.width}:${spec.height}:force_original_aspect_ratio=decrease," +
            "pad=${spec.width}:${spec.height}:(ow-iw)/2:(oh-ih)/2"

        val command = listOf(
            "ffmpeg",
            "-i", input.toString(),
            "-vf", scaleFilter,
            "-c:v", spec.codec,
            "-b:v", "${spec.bitrateKbps}k",
            "-c:a", spec.audioCodec,
            "-b:a", spec.audioBitrate,
            "-movflags", "+faststart",
            "-y",
            output.toString(),
        )

        log.debug("ffmpeg command: {}", command.joinToString(" "))

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        // ffmpeg 출력 로그 소비 (블로킹 방지)
        val processOutput = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            log.error("ffmpeg 실패 (exit={}): {}", exitCode, processOutput.takeLast(1000))
            throw RuntimeException("ffmpeg 트랜스코딩 실패 (exit code: $exitCode)")
        }

        if (!Files.exists(output) || Files.size(output) == 0L) {
            throw RuntimeException("ffmpeg 출력 파일이 생성되지 않았습니다")
        }
    }
}
