package com.ongo.infrastructure.videodownload

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.videodownload.DownloadedVideo
import com.ongo.application.videodownload.VideoSourceDownloader
import com.ongo.domain.videodownload.VideoDownloadProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * yt-dlp adapter. The binary and ffmpeg are deployment prerequisites; keeping
 * them outside the JVM avoids embedding platform-specific extraction logic.
 */
@Component
class YtDlpVideoDownloader(
    private val objectMapper: ObjectMapper,
    @param:Value("\${videodownload.yt-dlp-path:yt-dlp}")
    private val executable: String,
    @param:Value("\${videodownload.download-timeout-seconds:1200}")
    private val timeoutSeconds: Long,
) : VideoSourceDownloader {

    override fun download(url: String, provider: VideoDownloadProvider): DownloadedVideo {
        val directory = Files.createTempDirectory("ongo-video-download-")
        try {
            val metadataOutput = run(
                listOf(executable, "--dump-single-json", "--no-download", "--no-playlist", "--no-warnings", url),
                directory,
                60,
            )
            val metadata = objectMapper.readTree(metadataOutput)
            val title = metadata.path("title").asText("").take(200)

            run(
                listOf(
                    executable,
                    "--no-playlist",
                    "--no-part",
                    "--no-progress",
                    "--no-warnings",
                    "--format", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
                    "--merge-output-format", "mp4",
                    "--output", directory.resolve("%(id)s.%(ext)s").toString(),
                    url,
                ),
                directory,
                timeoutSeconds,
            )

            val file = Files.list(directory).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .filter { !it.fileName.toString().endsWith(".part") }
                    .findFirst()
                    .orElseThrow { IllegalStateException("yt-dlp가 영상 파일을 만들지 않았습니다") }
            }
            val size = Files.size(file)
            val extension = file.fileName.toString().substringAfterLast('.', "mp4")
            val contentType = when (extension.lowercase()) {
                "webm" -> "video/webm"
                "mov" -> "video/quicktime"
                else -> "video/mp4"
            }
            return DownloadedVideo(
                path = file,
                title = title,
                originalFilename = "${safeFilename(title)}.$extension",
                contentType = contentType,
                size = size,
            )
        } catch (e: Exception) {
            deleteDirectory(directory)
            throw IllegalStateException("외부 영상 추출에 실패했습니다 (${provider.name})", e)
        }
    }

    private fun run(command: List<String>, directory: Path, timeout: Long): String {
        val process = ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .start()
        val captured = StringBuilder()
        val outputReader = Thread.ofVirtual().start {
            process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            val buffer = CharArray(MAX_PROCESS_OUTPUT)
            while (true) {
                val read = reader.read(buffer)
                if (read == -1) break
                if (captured.length < MAX_PROCESS_OUTPUT) {
                    captured.append(buffer, 0, read.coerceAtMost(MAX_PROCESS_OUTPUT - captured.length))
                }
            }
            }
        }
        if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
            outputReader.join(1_000)
            throw IllegalStateException("외부 영상 추출 시간이 초과되었습니다")
        }
        outputReader.join(1_000)
        if (process.exitValue() != 0) {
            throw IllegalStateException("외부 영상 추출 명령이 실패했습니다")
        }
        return captured.toString()
    }

    private fun safeFilename(title: String): String = title
        .replace(Regex("[^a-zA-Z0-9가-힣._-]"), "_")
        .trim('_')
        .take(100)
        .ifBlank { "imported-video" }

    private fun deleteDirectory(directory: Path) {
        runCatching {
            Files.walk(directory).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    companion object {
        private const val MAX_PROCESS_OUTPUT = 16 * 1024
    }
}
