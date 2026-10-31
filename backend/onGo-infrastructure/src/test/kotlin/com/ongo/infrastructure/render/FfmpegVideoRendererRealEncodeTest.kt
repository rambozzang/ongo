package com.ongo.infrastructure.render

import com.ongo.domain.ugc.shorts.ClipRenderRequest
import com.ongo.domain.ugc.shorts.RenderFrameMode
import com.ongo.domain.ugc.shorts.RenderFramePoint
import com.ongo.domain.ugc.shorts.RenderFraming
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/**
 * **진짜 ffmpeg 로** 가로 원본을 세로로 만들어 결과 크기를 확인한다. 필터 문자열은 문법이 조금만
 * 틀려도 렌더 전체가 실패하는데, 문자열 비교 시험으로는 그것을 잡지 못한다.
 *
 * ffmpeg 가 없는 환경(CI 등)에서는 건너뛴다 — 없어서 못 돌린 것이지 통과한 것이 아니다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FfmpegVideoRendererRealEncodeTest {

    /** 자막 필터가 든 빌드가 있으면 그것을 쓴다(Homebrew 의 기본 ffmpeg 에는 libass 가 없다). */
    private val ffmpeg = listOf("/opt/homebrew/opt/ffmpeg-full/bin/ffmpeg", "ffmpeg").first { it == "ffmpeg" || Files.isExecutable(Path.of(it)) }
    private val ffprobe = ffmpeg.removeSuffix("ffmpeg") + "ffprobe"
    private lateinit var source: Path

    @BeforeAll
    fun makeSource() {
        assumeTrue(runs(listOf(ffmpeg, "-version")), "ffmpeg 가 없어 실제 인코딩 시험을 건너뛴다")
        source = Files.createTempFile("ongo-landscape-", ".mp4")
        val ok = runs(
            listOf(
                ffmpeg, "-v", "error", "-y",
                "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=15",
                "-f", "lavfi", "-i", "sine",
                "-t", "3", "-c:v", "libx264", "-preset", "ultrafast", "-c:a", "aac", source.toString(),
            ),
        )
        assumeTrue(ok, "시험용 원본을 만들 수 없다")
    }

    fun framings(): List<RenderFraming> = listOf(
        RenderFraming(RenderFrameMode.CROP, 0.5, 0.5),
        RenderFraming(RenderFrameMode.CROP, 0.0, 1.0, zoom = 0.5),
        RenderFraming(
            RenderFrameMode.CROP,
            path = listOf(RenderFramePoint(0, 0.1, 0.5), RenderFramePoint(1_000, 0.9, 0.5), RenderFramePoint(2_000, 0.4, 0.5)),
        ),
        RenderFraming(RenderFrameMode.FIT_BLUR),
        RenderFraming.FIT_BARS,
    )

    @ParameterizedTest
    @MethodSource("framings")
    fun `가로 원본이 늘어나지 않은 1080x1920 세로 영상이 된다`(framing: RenderFraming) {
        assertVertical(render(framing, subtitleAss = null))
    }

    /** 함께 실은 한글 글꼴로 자막을 굽는다. 자막 필터가 없는 빌드에서는 건너뛴다. */
    @ParameterizedTest
    @MethodSource("framings")
    fun `자막을 구워도 1080x1920 세로 영상이 된다`(framing: RenderFraming) {
        assumeTrue(
            FfmpegVideoRenderer.hasSubtitleFilter(output(listOf(ffmpeg, "-hide_banner", "-filters"))),
            "이 ffmpeg 에는 자막 필터가 없어 건너뛴다",
        )
        val ass = """
            [Script Info]
            ScriptType: v4.00+
            PlayResX: 1080
            PlayResY: 1920

            [V4+ Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
            Style: Caption,Pretendard,76,&H00FFFFFF,&H00FFFFFF,&H00000000,&H00000000,-1,0,0,0,100,100,0,0,1,3,0,2,40,40,60,1

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:00.00,0:00:01.50,Caption,,0,0,0,,구독과 {\c&H0000E1FF&\fscx112\fscy112}좋아요{\r}
        """.trimIndent()
        assertVertical(render(framing, ass))
    }

    private fun render(framing: RenderFraming, subtitleAss: String?) =
        FfmpegVideoRenderer(executable = ffmpeg, timeoutSeconds = 60, crf = 30, preset = "ultrafast").render(
            ClipRenderRequest(sourceUrl = source.toString(), startMs = 200, endMs = 2_600, framing = framing, subtitleAss = subtitleAss),
        )

    private fun assertVertical(rendered: com.ongo.domain.ugc.shorts.RenderedClip) {
        try {
            val out = output(
                listOf(
                    ffprobe, "-v", "error", "-select_streams", "v",
                    "-show_entries", "stream=width,height,sample_aspect_ratio", "-of", "csv=p=0", rendered.path.toString(),
                ),
            ).trim()
            assertEquals("1080,1920,1:1", out)
        } finally {
            rendered.path.parent.toFile().deleteRecursively()
        }
    }

    private fun output(command: List<String>): String {
        val p = ProcessBuilder(command).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        p.waitFor(30, TimeUnit.SECONDS)
        return text
    }

    private fun runs(command: List<String>): Boolean = runCatching {
        val p = ProcessBuilder(command).redirectErrorStream(true).start()
        p.inputStream.readAllBytes()
        p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0
    }.getOrDefault(false)
}
