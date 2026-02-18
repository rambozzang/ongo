package com.ongo.infrastructure.transcode

import com.ongo.application.video.ThumbnailService
import com.ongo.infrastructure.external.storage.StorageClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path

@Service
class ThumbnailGenerationService(
    private val storageClient: StorageClient,
) : ThumbnailService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun generateThumbnails(inputFilePath: String, videoId: Long, durationMs: Long?): List<String> {
        return generateThumbnails(inputFilePath, videoId, durationMs, 10)
    }

    fun generateThumbnails(inputFilePath: String, videoId: Long, durationMs: Long?, count: Int): List<String> {
        val tempDir = Path.of("/tmp/ongo-thumbnails/$videoId")
        Files.createDirectories(tempDir)

        try {
            val thumbnailFiles = mutableListOf<Path>()

            // Scene-detection based thumbnails (up to half)
            val sceneCount = count / 2
            val sceneThumbs = extractSceneDetectionFrames(inputFilePath, tempDir, sceneCount)
            thumbnailFiles.addAll(sceneThumbs)

            // Evenly-spaced thumbnails for the rest
            val evenCount = count - thumbnailFiles.size
            if (evenCount > 0 && durationMs != null && durationMs > 0) {
                val evenThumbs = extractEvenlySpacedFrames(inputFilePath, tempDir, evenCount, durationMs, thumbnailFiles.size)
                thumbnailFiles.addAll(evenThumbs)
            }

            if (thumbnailFiles.isEmpty()) {
                log.warn("썸네일 생성 실패: 추출된 프레임이 없습니다. videoId={}", videoId)
                return emptyList()
            }

            // Upload to storage
            val urls = thumbnailFiles.mapIndexed { index, file ->
                val key = "videos/$videoId/thumbnails/thumb_${index}.jpg"
                val fileSize = Files.size(file)
                val url = Files.newInputStream(file).use { inputStream ->
                    storageClient.uploadFile(key, inputStream, "image/jpeg", fileSize)
                }
                log.debug("썸네일 업로드: videoId={}, index={}, key={}", videoId, index, key)
                url
            }

            log.info("썸네일 생성 완료: videoId={}, count={}", videoId, urls.size)
            return urls
        } finally {
            try {
                Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            } catch (e: Exception) {
                log.warn("썸네일 임시 파일 정리 실패: {}", tempDir, e)
            }
        }
    }

    private fun extractSceneDetectionFrames(inputPath: String, tempDir: Path, count: Int): List<Path> {
        if (count <= 0) return emptyList()

        val outputPattern = tempDir.resolve("scene_%03d.jpg").toString()
        val command = listOf(
            "ffmpeg",
            "-i", inputPath,
            "-vf", "select='gt(scene,0.3)',scale=1280:-1",
            "-vsync", "vfr",
            "-frames:v", count.toString(),
            "-q:v", "2",
            "-y",
            outputPattern,
        )

        return runFfmpegAndCollect(command, tempDir, "scene_")
    }

    private fun extractEvenlySpacedFrames(inputPath: String, tempDir: Path, count: Int, durationMs: Long, startIdx: Int): List<Path> {
        if (count <= 0) return emptyList()

        val durationSec = durationMs / 1000.0
        val intervalSec = durationSec / (count + 1)

        // 단일 FFmpeg 프로세스로 모든 프레임 추출 — select 필터에 타임스탬프 조건 결합
        val timestamps = (1..count).map { intervalSec * it }
        val selectExpr = timestamps.joinToString("+") { t ->
            "lt(prev_pts*TB,${String.format("%.2f", t)})*gte(pts*TB,${String.format("%.2f", t)})"
        }

        val outputPattern = tempDir.resolve("even_%03d.jpg").toString()
        val command = listOf(
            "ffmpeg",
            "-i", inputPath,
            "-vf", "select='$selectExpr',scale=1280:-1",
            "-vsync", "vfr",
            "-frames:v", count.toString(),
            "-q:v", "2",
            "-y",
            outputPattern,
        )

        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                log.warn("균등 간격 프레임 추출 실패 (exit={}), 개별 추출 시도", exitCode)
                return extractEvenlySpacedFramesFallback(inputPath, tempDir, count, durationMs, startIdx)
            }

            Files.list(tempDir)
                .filter { it.fileName.toString().startsWith("even_") }
                .filter { Files.size(it) > 0 }
                .sorted()
                .toList()
        } catch (e: Exception) {
            log.warn("균등 간격 프레임 배치 추출 실패, 개별 추출 시도", e)
            extractEvenlySpacedFramesFallback(inputPath, tempDir, count, durationMs, startIdx)
        }
    }

    /**
     * 배치 추출 실패 시 폴백: 개별 -ss 시크로 프레임 추출 (느리지만 안정적)
     */
    private fun extractEvenlySpacedFramesFallback(inputPath: String, tempDir: Path, count: Int, durationMs: Long, startIdx: Int): List<Path> {
        val results = mutableListOf<Path>()
        val intervalSec = (durationMs / 1000.0) / (count + 1)

        for (i in 1..count) {
            val timestamp = intervalSec * i
            val outputFile = tempDir.resolve("even_${startIdx + i - 1}.jpg")
            val command = listOf(
                "ffmpeg",
                "-ss", String.format("%.2f", timestamp),
                "-i", inputPath,
                "-vf", "scale=1280:-1",
                "-frames:v", "1",
                "-q:v", "2",
                "-y",
                outputFile.toString(),
            )

            try {
                val process = ProcessBuilder(command).redirectErrorStream(true).start()
                process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                if (exitCode == 0 && Files.exists(outputFile) && Files.size(outputFile) > 0) {
                    results.add(outputFile)
                }
            } catch (e: Exception) {
                log.warn("균등 간격 프레임 추출 실패: index={}", i, e)
            }
        }

        return results
    }

    private fun runFfmpegAndCollect(command: List<String>, tempDir: Path, prefix: String): List<Path> {
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                log.warn("FFmpeg 씬 감지 실패 (exit={})", exitCode)
                return emptyList()
            }

            Files.list(tempDir)
                .filter { it.fileName.toString().startsWith(prefix) }
                .filter { Files.size(it) > 0 }
                .sorted()
                .toList()
        } catch (e: Exception) {
            log.warn("FFmpeg 실행 실패", e)
            emptyList()
        }
    }
}
