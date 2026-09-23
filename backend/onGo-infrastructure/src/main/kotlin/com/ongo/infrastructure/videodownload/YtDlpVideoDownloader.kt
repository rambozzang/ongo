package com.ongo.infrastructure.videodownload

import com.ongo.common.exception.BusinessException
import com.ongo.common.util.FileValidationUtil
import org.slf4j.LoggerFactory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.videodownload.DownloaderAvailability
import com.ongo.application.videodownload.DownloadedVideo
import com.ongo.application.videodownload.VideoSourceDownloader
import com.ongo.domain.videodownload.VideoDownloadProvider
import com.ongo.infrastructure.runtime.RuntimeExecutableResolver
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
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
    @param:Value("\${videodownload.download-timeout-seconds:3600}")
    private val timeoutSeconds: Long,
) : VideoSourceDownloader {

    private val resolvedExecutable = RuntimeExecutableResolver.resolve(executable)
    private val ffmpegLocation = RuntimeExecutableResolver.resolve("ffmpeg")

    private val log = LoggerFactory.getLogger(javaClass)

    private val cached = AtomicReference<Pair<Instant, DownloaderAvailability>?>(null)

    override fun download(url: String, provider: VideoDownloadProvider): DownloadedVideo {
        val directory = Files.createTempDirectory("ongo-video-download-")
        try {
            val metadataOutput = run(
                listOf(resolvedExecutable, "--dump-single-json", "--no-download", "--no-playlist", "--no-warnings", url),
                directory,
                60,
            )
            val metadata = objectMapper.readTree(metadataOutput)
            val title = metadata.path("title").asText("").take(200)
            checkCapacity(estimatedSize(metadata), directory.toFile().usableSpace)

            val downloadCommand = buildList {
                add(resolvedExecutable)
                addAll(
                    listOf(
                        "--no-playlist",
                        "--no-part",
                        "--no-progress",
                        "--no-warnings",
                        "--format", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
                        "--merge-output-format", "mp4",
                        // 메타데이터에 크기가 없는 영상도 있다. 받는 도중에 상한을 넘으면 yt-dlp 가 멈춘다.
                        "--max-filesize", FileValidationUtil.VIDEO_DIRECT_UPLOAD_MAX_BYTES.toString(),
                    ),
                )
                addAll(ffmpegLocationArgs())
                addAll(
                    listOf(
                        "--output", directory.resolve("%(id)s.%(ext)s").toString(),
                        url,
                    ),
                )
            }
            run(downloadCommand, directory, timeoutSeconds)

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
        } catch (e: VideoTooLargeException) {
            deleteDirectory(directory)
            throw e
        } catch (e: Exception) {
            deleteDirectory(directory)
            throw IllegalStateException("외부 영상 추출에 실패했습니다 (${provider.name})", e)
        }
    }


    /**
     * `yt-dlp --version` 을 실행해 바이너리가 있는지 본다.
     *
     * 결과를 캐시하는 이유는 이 조회가 **화면을 열 때마다** 불리기 때문이다. 매번 프로세스를
     * 띄우면 조회 자체가 부하가 된다. 반대로 영구 캐시는 배포로 설치한 뒤에도 계속
     * "없음"을 답하므로 [AVAILABILITY_TTL] 만큼만 유지한다.
     *
     * 어떤 예외도 밖으로 내보내지 않는다. "쓸 수 없다"는 정상적인 답이다.
     */
    override fun checkAvailability(): DownloaderAvailability {
        cached.get()?.let { (at, value) ->
            if (Duration.between(at, Instant.now()) < AVAILABILITY_TTL) return value
        }

        val result = probe()
        cached.set(Instant.now() to result)
        return result
    }

    private fun probe(): DownloaderAvailability =
        try {
            // 임시 디렉터리에서 돌린다. 작업 디렉터리에 아무것도 만들지 않는다.
            val dir = Files.createTempDirectory("ongo-ytdlp-probe-")
            try {
                run(listOf(resolvedExecutable, "--version"), dir, PROBE_TIMEOUT_SECONDS)
                DownloaderAvailability(available = true)
            } finally {
                runCatching { Files.deleteIfExists(dir) }
            }
        } catch (e: Exception) {
            // 경로나 예외 메시지를 그대로 노출하지 않는다. 내부 구조가 새고,
            // 사용자가 그걸 보고 할 수 있는 일도 없다. 진단은 로그로 남긴다.
            log.warn("영상 추출기를 사용할 수 없다. executable={} 원인={}", resolvedExecutable, e.message)
            DownloaderAvailability(
                available = false,
                reason = "영상 URL 가져오기를 지금 사용할 수 없습니다. 관리자에게 문의해 주세요.",
            )
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

    private fun ffmpegLocationArgs(): List<String> =
        RuntimeExecutableResolver.directoryOf(ffmpegLocation)
            ?.let { listOf("--ffmpeg-location", it) }
            ?: emptyList()

    private fun deleteDirectory(directory: Path) {
        runCatching {
            Files.walk(directory).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    companion object {
        /**
         * 받기 전에 알 수 있는 크기. 병합 형식이면 요청된 형식들의 합이다. 모르면 null.
         * yt-dlp 는 정확한 `filesize` 가 없을 때 `filesize_approx` 를 준다.
         */
        internal fun estimatedSize(metadata: JsonNode): Long? {
            fun sizeOf(node: JsonNode): Long? =
                node.path("filesize").takeIf { it.isNumber }?.asLong()
                    ?: node.path("filesize_approx").takeIf { it.isNumber }?.asLong()
            sizeOf(metadata)?.let { return it }
            val parts = metadata.path("requested_formats").takeIf { it.isArray }?.map(::sizeOf) ?: return null
            return if (parts.isNotEmpty() && parts.all { it != null }) parts.sumOf { it!! } else null
        }

        /**
         * 받기 전에 거른다. 상한을 넘는 영상을 끝까지 받은 뒤 버리면 대역폭과 시간을 버리고, 디스크가
         * 모자라면 다른 작업(쇼츠 렌더·게시 임시 파일)까지 함께 실패한다.
         *
         * 영상·음성을 따로 받아 합치는 동안 원본 둘과 결과물이 함께 있으므로 **예상 크기의 2배**에 여유를 더한다.
         * 크기를 모르면 디스크는 판단하지 않는다 — `--max-filesize` 와 다운로드 후 검증이 막는다.
         */
        internal fun checkCapacity(estimatedBytes: Long?, usableBytes: Long) {
            if (estimatedBytes == null) return
            if (estimatedBytes > FileValidationUtil.VIDEO_DIRECT_UPLOAD_MAX_BYTES) {
                throw VideoTooLargeException(estimatedBytes)
            }
            val needed = estimatedBytes * 2 + DISK_HEADROOM_BYTES
            if (usableBytes < needed) {
                throw IllegalStateException(
                    "서버 임시 공간이 부족합니다 (필요 ${needed / MIB}MB, 여유 ${usableBytes / MIB}MB)",
                )
            }
        }

        private const val MIB = 1024L * 1024
        private const val DISK_HEADROOM_BYTES = 512L * MIB
        private val AVAILABILITY_TTL: Duration = Duration.ofMinutes(5)
        private const val PROBE_TIMEOUT_SECONDS = 5L
        private const val MAX_PROCESS_OUTPUT = 16 * 1024
    }
}

/** 받기 전에 크기가 상한을 넘는다고 확인된 영상. 사용자에게 한도를 그대로 알린다. */
class VideoTooLargeException(val estimatedBytes: Long) : BusinessException(
    "VIDEO_DOWNLOAD_SIZE_INVALID",
    "영상이 너무 큽니다(약 ${estimatedBytes / (1024 * 1024 * 1024)}GB). 최대 10GB 까지 가져올 수 있습니다.",
)
