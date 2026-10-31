package com.ongo.infrastructure.render

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 진짜 ffmpeg 로 장면 격자를 만든다. ffmpeg·ffprobe 가 없으면 건너뛴다. */
class FfmpegFrameSamplerTest {

    private fun runs(vararg command: String): Boolean = runCatching {
        val p = ProcessBuilder(*command).redirectErrorStream(true).start()
        p.inputStream.readAllBytes()
        p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0
    }.getOrDefault(false)

    @Test
    fun `클립 구간에서 12장면 격자 JPEG 와 원본 크기와 칸별 시각을 만든다`() {
        assumeTrue(runs("ffmpeg", "-version") && runs("ffprobe", "-version"), "ffmpeg 가 없어 건너뛴다")
        val source = Files.createTempFile("ongo-sampler-", ".mp4")
        try {
            assumeTrue(
                runs(
                    "ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=15",
                    "-t", "6", "-c:v", "libx264", "-preset", "ultrafast", source.toString(),
                ),
            )

            val sheet = FfmpegFrameSampler("ffmpeg", "ffprobe", 60).contactSheet(source.toString(), 1_000, 5_800)

            assertEquals(640, sheet.sourceWidth)
            assertEquals(360, sheet.sourceHeight)
            assertEquals(12, sheet.tileTimesMs.size)
            assertEquals(0L, sheet.tileTimesMs.first())
            assertEquals(11 * 400L, sheet.tileTimesMs.last())
            // JPEG 시작 표식
            assertTrue(sheet.image.size > 1_000 && sheet.image[0] == 0xFF.toByte() && sheet.image[1] == 0xD8.toByte())
        } finally {
            Files.deleteIfExists(source)
        }
    }

    @Test
    fun `원본을 읽지 못하면 사용자에게 보여도 되는 문장으로 실패한다`() {
        assumeTrue(runs("ffprobe", "-version"), "ffprobe 가 없어 건너뛴다")

        val error = assertThrows<IllegalStateException> {
            FfmpegFrameSampler("ffmpeg", "ffprobe", 30).contactSheet("/nonexistent/secret/video.mp4", 0, 1_000)
        }

        assertTrue(!error.message!!.contains("secret"), error.message)
    }
}
