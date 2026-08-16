package com.ongo.infrastructure.render

import com.ongo.domain.ugc.shorts.ClipRenderRequest
import com.ongo.domain.ugc.shorts.RenderedClip
import com.ongo.domain.ugc.shorts.RendererAvailability
import com.ongo.domain.ugc.shorts.VideoRenderer
import com.ongo.infrastructure.runtime.RuntimeExecutableResolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * ffmpeg 어댑터. 크롭 → 스케일 → 자막 번인 순서는
 * `ShortsRenderSpecBuilder.buildRenderScript` 가 만드는 명령과 같다.
 *
 * ## 셸을 거치지 않는다
 *
 * 산출물 `render.sh` 는 `sourceFileUrl` 을 셸 문자열에 끼워 넣는다. 사용자가 손으로 돌릴 때는
 * 그 자체가 사용자 책임이지만, **서버가 돌린다면 얘기가 다르다.** URL 은 외부에서 온 값이라
 * 셸 메타문자가 명령으로 해석될 수 있다.
 *
 * 그래서 여기서는 `ProcessBuilder` 에 **인자 배열**을 넘긴다. 셸이 없으므로 해석될 통로도 없다.
 *
 * ## 임시 디렉터리를 격리한다
 *
 * 자막 파일과 출력물을 요청마다 새 디렉터리에 만든다. 동시에 여러 렌더가 돌아도 파일 이름이
 * 겹치지 않고, 실패해도 그 디렉터리만 지우면 된다.
 */
@Component
class FfmpegVideoRenderer(
    @param:Value("\${shorts.render.ffmpeg-path:ffmpeg}")
    private val executable: String,
    @param:Value("\${shorts.render.timeout-seconds:1800}")
    private val timeoutSeconds: Long,
    @param:Value("\${shorts.render.crf:20}")
    private val crf: Int,
    @param:Value("\${shorts.render.preset:medium}")
    private val preset: String,
) : VideoRenderer {

    private val resolvedExecutable = RuntimeExecutableResolver.resolve(executable)

    private val log = LoggerFactory.getLogger(javaClass)
    private val cachedAvailability = AtomicReference<Pair<Instant, RendererAvailability>?>(null)

    override fun render(request: ClipRenderRequest): RenderedClip {
        val workDir = Files.createTempDirectory("ongo-shorts-render-")
        try {
            val subtitlePath = request.subtitleAss
                ?.takeIf { it.isNotBlank() }
                ?.let { ass ->
                    workDir.resolve("subtitle.ass").also {
                        Files.writeString(it, ass, StandardCharsets.UTF_8)
                    }
                }

            val output = workDir.resolve("clip.mp4")
            runProcess(buildCommand(request, subtitlePath, output), workDir, timeoutSeconds)

            if (!Files.exists(output) || Files.size(output) == 0L) {
                // ffmpeg 가 0 으로 끝나고도 결과를 못 만드는 경우가 있다. 크기까지 봐야 안다.
                throw IllegalStateException("인코더가 영상 파일을 만들지 않았습니다")
            }

            // 성공하면 출력만 남기고 작업 디렉터리는 호출자가 정리하도록 경로를 넘긴다.
            // 여기서 지우면 파일이 사라지므로 지우지 않는다.
            return RenderedClip(path = output, sizeBytes = Files.size(output))
        } catch (e: Exception) {
            // 실패 경로에서는 우리가 만든 것을 우리가 치운다. 안 그러면 디스크가 찬다.
            deleteRecursively(workDir)
            throw e
        }
    }

    /**
     * 필터 체인은 `크롭 → 스케일 → 자막` 순서다. 순서가 바뀌면 결과가 달라진다 —
     * 스케일 뒤에 크롭하면 좌표가 원본 기준이 아니게 되고, 자막을 먼저 구우면 크롭에 잘린다.
     */
    private fun buildCommand(
        request: ClipRenderRequest,
        subtitlePath: Path?,
        output: Path,
    ): List<String> {
        val filters = buildList {
            request.crop?.let { add("crop=${it.width}:${it.height}:${it.x}:${it.y}") }
            add("scale=${request.outputWidth}:${request.outputHeight}")
            // 파일명에 콜론·백슬래시가 있으면 필터 문법이 깨진다. 작업 디렉터리에서
            // 고정된 이름으로만 쓰므로 상대 경로를 그대로 넘긴다.
            subtitlePath?.let { add("ass=${it.fileName}") }
        }

        return listOf(
            executable,
            "-nostdin",              // 파이프에서 입력을 기다리다 멈추는 것을 막는다
            "-y",                    // 출력 덮어쓰기. 매번 새 디렉터리라 충돌은 없다
            "-ss", seconds(request.startMs),
            "-to", seconds(request.endMs),
            "-i", request.sourceUrl,
            "-vf", filters.joinToString(","),
            "-c:v", "libx264",
            "-preset", preset,
            "-crf", crf.toString(),
            "-c:a", "aac",
            "-b:a", "128k",
            output.fileName.toString(),
        )
    }

    override fun checkAvailability(): RendererAvailability {
        cachedAvailability.get()?.let { (at, value) ->
            if (Duration.between(at, Instant.now()) < AVAILABILITY_TTL) return value
        }
        val result = probe()
        cachedAvailability.set(Instant.now() to result)
        return result
    }

    private fun probe(): RendererAvailability =
        try {
            val dir = Files.createTempDirectory("ongo-ffmpeg-probe-")
            try {
            runProcess(listOf(resolvedExecutable, "-version"), dir, PROBE_TIMEOUT_SECONDS)
                RendererAvailability(available = true)
            } finally {
                deleteRecursively(dir)
            }
        } catch (e: Exception) {
            log.warn("영상 인코더를 사용할 수 없다. executable={} 원인={}", resolvedExecutable, e.message)
            RendererAvailability(
                available = false,
                reason = "영상 렌더링을 지금 사용할 수 없습니다. 관리자에게 문의해 주세요.",
            )
        }

    private fun runProcess(command: List<String>, directory: Path, timeout: Long) {
        // 셸을 거치지 않는다. 인자 배열이라 메타문자가 해석될 통로가 없다.
        val process = ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .start()

        val captured = StringBuilder()
        val reader = Thread.ofVirtual().start {
            process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { r ->
                val buffer = CharArray(8_192)
                while (true) {
                    val read = r.read(buffer)
                    if (read == -1) break
                    if (captured.length < MAX_OUTPUT) {
                        captured.append(buffer, 0, read.coerceAtMost(MAX_OUTPUT - captured.length))
                    }
                }
            }
        }

        if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
            reader.join(1_000)
            throw IllegalStateException("영상 렌더링 시간이 초과되었습니다")
        }
        reader.join(1_000)

        if (process.exitValue() != 0) {
            // 출력에 경로가 섞이므로 예외 메시지에 넣지 않는다. 진단은 로그가 맡는다.
            log.warn("인코더 실패. exit={} output={}", process.exitValue(), captured.toString().takeLast(500))
            throw IllegalStateException("영상 렌더링에 실패했습니다")
        }
    }

    private fun deleteRecursively(dir: Path) {
        runCatching {
            if (!Files.exists(dir)) return
            Files.walk(dir).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.delete(it) } }
            }
        }
    }

    private fun seconds(ms: Long): String = String.format("%.3f", ms / 1000.0)

    companion object {
        private val AVAILABILITY_TTL: Duration = Duration.ofMinutes(5)
        private const val PROBE_TIMEOUT_SECONDS = 5L
        private const val MAX_OUTPUT = 16_384
    }
}
