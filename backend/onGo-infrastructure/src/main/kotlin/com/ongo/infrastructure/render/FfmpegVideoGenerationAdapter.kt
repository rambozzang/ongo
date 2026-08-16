package com.ongo.infrastructure.render

import com.ongo.application.video.GeneratedVideoFile
import com.ongo.application.video.VideoGenerationPort
import com.ongo.application.video.VideoGenerationSpec
import com.ongo.application.video.TextToSpeechPort
import com.ongo.infrastructure.runtime.RuntimeExecutableResolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit

/**
 * 외부 셸을 거치지 않고 ffmpeg 인자 배열로 텍스트 슬라이드 영상을 만든다.
 * prompt는 textfile 입력으로만 전달하여 셸/필터 문자열 주입을 차단한다.
 */
@Component
class FfmpegVideoGenerationAdapter(
    private val textToSpeechPort: TextToSpeechPort,
    @param:Value("\${shorts.render.ffmpeg-path:ffmpeg}")
    private val executable: String,
    @param:Value("\${video.generation.timeout-seconds:180}")
    private val timeoutSeconds: Long,
) : VideoGenerationPort {

    private val log = LoggerFactory.getLogger(javaClass)
    private val resolvedExecutable = RuntimeExecutableResolver.resolve(executable)

    override fun generate(request: VideoGenerationSpec): GeneratedVideoFile {
        val workDir = Files.createTempDirectory("ongo-video-generation-")
        try {
            val textFile = workDir.resolve("slide.txt")
            Files.writeString(textFile, request.prompt, StandardCharsets.UTF_8)
            val output = workDir.resolve("generated.mp4")
            val filter = "drawtext=textfile=${filterPath(textFile)}:fontcolor=white:fontsize=64:line_spacing=14:x=(w-text_w)/2:y=(h-text_h)/2:box=1:boxcolor=black@0.35:boxborderw=28"
            val audio = request.voice?.trim()?.takeIf { it.isNotBlank() }?.let {
                textToSpeechPort.synthesize(request.prompt, it)
            }
            val hasAudio = audio != null
            val command = listOf(
                resolvedExecutable,
                "-nostdin",
                "-y",
                "-f", "lavfi",
                "-i", "color=c=0x111827:s=${request.orientation.width}x${request.orientation.height}:r=30:d=${if (hasAudio) 120 else 8}",
                "-vf", filter,
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                "-movflags", "+faststart",
            ).toMutableList().apply {
                if (audio != null) {
                    addAll(listOf("-i", audio.path.toString(), "-shortest", "-c:a", "aac", "-b:a", "128k"))
                } else {
                    add("-an")
                }
                add(output.fileName.toString())
            }
            try {
                runProcess(command, workDir)
            } finally {
                audio?.let { Files.deleteIfExists(it.path) }
            }
            require(Files.isRegularFile(output) && Files.size(output) > 0) {
                "영상 인코더가 결과 파일을 만들지 않았습니다"
            }
            return GeneratedVideoFile(output, Files.size(output))
        } catch (error: Exception) {
            deleteRecursively(workDir)
            throw error
        }
    }

    private fun filterPath(path: Path): String = path.toAbsolutePath().toString()
        .replace("\\", "/")
        .replace(":", "\\:")
        .replace("'", "\\'")

    private fun runProcess(command: List<String>, workDir: Path) {
        val process = ProcessBuilder(command)
            .directory(workDir.toFile())
            .redirectErrorStream(true)
            .start()
        // Drain ffmpeg output concurrently. Reading it on the caller thread would
        // block before waitFor() and make the timeout ineffective when ffmpeg keeps
        // the pipe open.
        val captured = AtomicReference("")
        val reader = Thread.ofVirtual().start {
            val text = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            captured.set(text.takeLast(4_000))
        }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            reader.join(1_000)
            throw IllegalStateException("영상 생성 시간이 초과되었습니다")
        }
        reader.join(1_000)
        if (process.exitValue() != 0) {
            log.warn("텍스트 슬라이드 영상 생성 실패: {}", captured.get())
            throw IllegalStateException("텍스트 슬라이드 영상 생성에 실패했습니다")
        }
    }

    private fun deleteRecursively(dir: Path) {
        runCatching {
            if (!Files.exists(dir)) return
            Files.walk(dir).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.deleteIfExists(it) } }
            }
        }
    }
}
