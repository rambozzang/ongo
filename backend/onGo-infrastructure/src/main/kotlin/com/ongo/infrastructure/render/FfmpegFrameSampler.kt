package com.ongo.infrastructure.render

import com.ongo.domain.ugc.shorts.FrameSampler
import com.ongo.domain.ugc.shorts.FrameSheet
import com.ongo.infrastructure.runtime.RuntimeExecutableResolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * ffmpeg 로 클립 구간의 장면 격자를 만든다. 렌더러와 같이 **셸을 거치지 않고** 인자 배열로 실행한다.
 *
 * 격자는 4×3(12칸), 칸 너비 [TILE_WIDTH]. 16:9 원본이면 1280×540 — 비전 모델 입력 약 1,000 토큰이다.
 */
@Component
class FfmpegFrameSampler(
    @Value("\${shorts.render.ffmpeg-path:ffmpeg}") executable: String,
    @Value("\${shorts.transcribe.ffprobe-path:ffprobe}") probeExecutable: String,
    @param:Value("\${shorts.track.timeout-seconds:120}") private val timeoutSeconds: Long,
) : FrameSampler {

    private val ffmpeg = RuntimeExecutableResolver.resolve(executable)
    private val ffprobe = RuntimeExecutableResolver.resolve(probeExecutable)
    private val log = LoggerFactory.getLogger(javaClass)

    override fun contactSheet(sourceUrl: String, startMs: Long, endMs: Long): FrameSheet {
        require(endMs > startMs) { "클립 끝이 시작보다 커야 한다" }
        val workDir = Files.createTempDirectory("ongo-shorts-track-")
        try {
            val (width, height) = probeSize(sourceUrl, workDir)
            val durationSec = (endMs - startMs) / 1000.0
            val tiles = COLUMNS * ROWS
            val output = workDir.resolve("sheet.jpg")
            run(
                listOf(
                    ffmpeg, "-nostdin", "-y", "-v", "error",
                    "-ss", sec(startMs), "-to", sec(endMs), "-i", sourceUrl,
                    // 구간을 칸 수로 나눈 간격마다 한 장씩, 칸 크기로 줄여 격자에 붙인다.
                    "-vf", "fps=$tiles/${fmt(durationSec)},scale=$TILE_WIDTH:-2,tile=${COLUMNS}x$ROWS",
                    "-frames:v", "1", "-q:v", "4",
                    output.fileName.toString(),
                ),
                workDir,
            )
            if (!Files.exists(output) || Files.size(output) == 0L) {
                throw IllegalStateException("장면 이미지를 만들지 못했습니다")
            }
            val step = (endMs - startMs) / tiles
            return FrameSheet(
                image = Files.readAllBytes(output),
                mimeType = "image/jpeg",
                columns = COLUMNS,
                rows = ROWS,
                tileTimesMs = (0 until tiles).map { it * step },
                sourceWidth = width,
                sourceHeight = height,
            )
        } finally {
            workDir.toFile().deleteRecursively()
        }
    }

    private fun probeSize(sourceUrl: String, workDir: Path): Pair<Int, Int> {
        val out = run(
            listOf(
                ffprobe, "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=width,height", "-of", "csv=p=0:s=x", sourceUrl,
            ),
            workDir,
        ).trim().lineSequence().firstOrNull().orEmpty()
        val parts = out.split("x").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size != 2 || parts.any { it <= 0 }) throw IllegalStateException("원본 영상 크기를 읽지 못했습니다")
        return parts[0] to parts[1]
    }

    private fun run(command: List<String>, dir: Path): String {
        val process = ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start()
        val output = StringBuilder()
        val reader = Thread.ofVirtual().start {
            process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { r ->
                val buf = CharArray(4_096)
                while (true) {
                    val n = r.read(buf)
                    if (n == -1) break
                    if (output.length < MAX_OUTPUT) output.append(buf, 0, n.coerceAtMost(MAX_OUTPUT - output.length))
                }
            }
        }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            reader.join(1_000)
            throw IllegalStateException("장면 추출 시간이 초과되었습니다")
        }
        reader.join(1_000)
        if (process.exitValue() != 0) {
            // 출력에 원본 URL(서명 포함)이 섞일 수 있어 예외 메시지에는 넣지 않는다.
            log.warn("장면 추출 실패. exit={} output={}", process.exitValue(), output.toString().takeLast(300))
            throw IllegalStateException("원본 영상에서 장면을 추출하지 못했습니다")
        }
        return output.toString()
    }

    private fun sec(ms: Long) = String.format(Locale.ROOT, "%.3f", ms / 1000.0)
    private fun fmt(v: Double) = String.format(Locale.ROOT, "%.3f", v)

    companion object {
        const val COLUMNS = 4
        const val ROWS = 3
        const val TILE_WIDTH = 320
        private const val MAX_OUTPUT = 16_384
    }
}
