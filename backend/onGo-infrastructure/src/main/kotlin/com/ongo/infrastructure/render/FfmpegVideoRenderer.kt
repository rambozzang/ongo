package com.ongo.infrastructure.render

import com.ongo.domain.ugc.shorts.ClipRenderRequest
import com.ongo.domain.ugc.shorts.RenderedClip
import com.ongo.domain.ugc.shorts.RendererAvailability
import com.ongo.domain.ugc.shorts.ShortsFilterGraph
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
 * ffmpeg 어댑터. 화면 구성 → 스케일 → 자막 번인 필터는 [ShortsFilterGraph] 하나에서 나오고,
 * `ShortsRenderSpecBuilder.buildRenderScript` 도 같은 것을 쓴다.
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
    /*
     * **설정값을 프로퍼티로 두지 않는다.**
     *
     * 예전에는 `private val` 이라 클래스 어디서나 읽을 수 있었고, 실제로 [checkAvailability]
     * 는 해석된 경로로 확인하면서 [buildCommand] 는 설정 원본을 그대로 실행했다. 배포는
     * ffmpeg 를 `/data/ffmpeg/bin` 같은 곳에 두므로 서비스 사용자의 PATH 에는 없다 —
     * **가용성은 "쓸 수 있다"고 답하고 실제 렌더는 실행 파일을 못 찾아 실패했다.**
     * 화면은 그 응답으로 렌더 버튼을 노출하므로, 사용자는 앞 단계 크레딧을 쓴 뒤에야 알게 된다.
     *
     * 생성자 파라미터로만 두면 멤버 함수에서 참조할 수 없어 **같은 실수가 컴파일되지 않는다.**
     * 형제 어댑터(FfmpegVideoGenerationAdapter·FfmpegTranscriptionAudioAdapter·
     * YtDlpVideoDownloader)는 모두 해석된 경로로 실행한다.
     */
    @Value("\${shorts.render.ffmpeg-path:ffmpeg}")
    executable: String,
    @param:Value("\${shorts.render.timeout-seconds:1800}")
    private val timeoutSeconds: Long,
    @param:Value("\${shorts.render.crf:20}")
    private val crf: Int,
    @param:Value("\${shorts.render.preset:medium}")
    private val preset: String,
) : VideoRenderer {

    /** 확인과 실행이 **반드시 같은 바이너리를 가리켜야 한다.** 이 값 하나만 쓴다. */
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

            val fontsDir = subtitlePath?.let { installFonts(workDir) }
            val output = workDir.resolve("clip.mp4")
            runProcess(buildCommand(request, subtitlePath, fontsDir, output), workDir, timeoutSeconds)

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
     * 필터는 [ShortsFilterGraph] 가 만든다 — 내려받는 `render.sh` 와 같은 함수다.
     * 자막 파일명에 콜론·백슬래시가 있으면 필터 문법이 깨지므로, 작업 디렉터리 안의
     * 고정된 이름만 넘긴다.
     */
    private fun buildCommand(
        request: ClipRenderRequest,
        subtitlePath: Path?,
        fontsDir: Path?,
        output: Path,
    ): List<String> {
        val filters = ShortsFilterGraph.build(
            framing = request.framing,
            width = request.outputWidth,
            height = request.outputHeight,
            subtitleFile = subtitlePath?.fileName?.toString(),
            fontsDir = fontsDir?.fileName?.toString(),
        )

        return listOf(
            resolvedExecutable,
            "-nostdin",              // 파이프에서 입력을 기다리다 멈추는 것을 막는다
            "-y",                    // 출력 덮어쓰기. 매번 새 디렉터리라 충돌은 없다
            "-ss", seconds(request.startMs),
            "-to", seconds(request.endMs),
            "-i", request.sourceUrl,
            "-vf", filters,
            "-c:v", "libx264",
            "-preset", preset,
            "-crf", crf.toString(),
            "-c:a", "aac",
            "-b:a", "128k",
            output.fileName.toString(),
        )
    }

    /**
     * 함께 실은 한글 글꼴(SIL OFL)을 작업 디렉터리에 푼다. 서버에 한글 글꼴이 없으면 자막이
     * 네모(□)로 구워지는데 ffmpeg 는 성공으로 끝난다 — 사용자는 깨진 영상을 받는다.
     * 글꼴을 못 풀면 시스템 글꼴로 진행한다(렌더 자체를 막을 이유는 아니다).
     */
    private fun installFonts(workDir: Path): Path? = runCatching {
        val dir = Files.createDirectories(workDir.resolve("fonts"))
        BUNDLED_FONTS.forEach { name ->
            val stream = javaClass.getResourceAsStream("/fonts/shorts/$name") ?: error("글꼴 없음: $name")
            stream.use { Files.copy(it, dir.resolve(name)) }
        }
        dir
    }.onFailure { log.warn("자막 글꼴을 준비하지 못했다. 시스템 글꼴로 진행한다: {}", it.message) }.getOrNull()

    override fun checkAvailability(): RendererAvailability {
        cachedAvailability.get()?.let { (at, value) ->
            if (Duration.between(at, Instant.now()) < AVAILABILITY_TTL) return value
        }
        val result = probe()
        cachedAvailability.set(Instant.now() to result)
        return result
    }

    /**
     * 실행 여부만이 아니라 **자막 필터(libass)가 들어 있는지**까지 본다. 자막을 굽지 못하는
     * 빌드(예: 최소 구성 ffmpeg)는 렌더 마지막에야 "필터 없음"으로 실패하는데, 그때는 앞 단계
     * 크레딧을 이미 쓴 뒤다.
     */
    private fun probe(): RendererAvailability =
        try {
            val dir = Files.createTempDirectory("ongo-ffmpeg-probe-")
            try {
                val filters = runProcess(listOf(resolvedExecutable, "-hide_banner", "-filters"), dir, PROBE_TIMEOUT_SECONDS)
                if (hasSubtitleFilter(filters)) {
                    RendererAvailability(available = true)
                } else {
                    log.warn("영상 인코더에 자막 필터(ass)가 없다. libass 가 포함된 ffmpeg 가 필요하다. executable={}", resolvedExecutable)
                    RendererAvailability(
                        available = false,
                        reason = "서버 영상 인코더가 자막 굽기를 지원하지 않아 렌더링할 수 없습니다. 관리자에게 문의해 주세요.",
                    )
                }
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

    /** 실행하고 출력(앞부분 최대 [MAX_OUTPUT] 자)을 돌려준다. 실패하면 예외. */
    private fun runProcess(command: List<String>, directory: Path, timeout: Long): String {
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
        return captured.toString()
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
        /** `ffmpeg -filters` 목록에 `ass` 필터 줄이 있는지. 형식: ` T.. ass               V->V       Render ASS ...` */
        fun hasSubtitleFilter(filtersOutput: String): Boolean =
            filtersOutput.lineSequence().any { line -> line.trim().split(Regex("\\s+")).getOrNull(1) == "ass" }

        private val AVAILABILITY_TTL: Duration = Duration.ofMinutes(5)
        private const val PROBE_TIMEOUT_SECONDS = 5L
        private const val MAX_OUTPUT = 65_536
        private val BUNDLED_FONTS = listOf("Pretendard-ExtraBold.otf")
    }
}
