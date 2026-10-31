package com.ongo.domain.ugc.shorts

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 필터 문자열의 구조를 고정한다. 실제 ffmpeg 로 돌려 보는 시험은 인프라 모듈에 있다
 * (`FfmpegVideoRendererRealEncodeTest`).
 */
class ShortsFilterGraphTest {

    @Test
    fun `크롭은 입력 크기에서 9대16 창을 계산하고 위치를 원본 안으로 묶는다`() {
        val vf = ShortsFilterGraph.build(RenderFraming(RenderFrameMode.CROP, 0.3, 0.5), 1080, 1920, "s.ass")

        assertTrue(vf.startsWith("crop=w=trunc(min(ih\\,iw*1920/1080)*1.0000*1080/1920/2)*2:h=trunc(min(ih\\,iw*1920/1080)*1.0000/2)*2:"), vf)
        assertTrue(vf.contains("x=clip(0.3000*iw-ow/2\\,0\\,iw-ow)"), vf)
        assertTrue(vf.contains("y=clip(0.5000*ih-oh/2\\,0\\,ih-oh)"), vf)
        assertTrue(vf.endsWith(",scale=1080:1920,setsar=1,ass=s.ass"), vf)
    }

    @Test
    fun `창 경로는 지점 사이를 선형으로 잇고 앞뒤는 끝값에 고정한다`() {
        val framing = RenderFraming(
            RenderFrameMode.CROP,
            path = listOf(RenderFramePoint(1_000, 0.2, 0.5), RenderFramePoint(3_000, 0.8, 0.5)),
        )
        val vf = ShortsFilterGraph.build(framing, 1080, 1920, null)

        assertTrue(vf.contains("if(lt(t\\,1.000)\\,0.2000\\,if(lt(t\\,3.000)\\,0.2000+(0.6000)*(t-1.000)/2.000\\,0.8000))"), vf)
        assertFalse(vf.contains("ass="), vf)
    }

    @Test
    fun `맞춤 모드는 원본 비율을 지키고 늘이지 않는다`() {
        val bars = ShortsFilterGraph.build(RenderFraming.FIT_BARS, 1080, 1920, null)
        assertTrue(bars.contains("force_original_aspect_ratio=decrease") && bars.contains("pad=1080:1920"), bars)

        val blur = ShortsFilterGraph.build(RenderFraming(RenderFrameMode.FIT_BLUR), 1080, 1920, "a.ass")
        assertTrue(blur.startsWith("split=2[bg][fg];"), blur)
        assertTrue(blur.contains("boxblur") && blur.contains("overlay=(W-w)/2:(H-h)/2"), blur)
        assertTrue(blur.endsWith(",ass=a.ass"), blur)
    }

    @Test
    fun `숫자는 지역 설정과 무관하게 점으로 적는다`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            val vf = ShortsFilterGraph.build(RenderFraming(RenderFrameMode.CROP, 0.25, 0.5), 1080, 1920, null)
            assertTrue(vf.contains("0.2500*iw"), vf)
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `잘못된 화면 구성은 만들 수 없다`() {
        assertThrows<IllegalArgumentException> { RenderFraming(RenderFrameMode.CROP, centerX = 1.5) }
        assertThrows<IllegalArgumentException> { RenderFraming(RenderFrameMode.CROP, zoom = 0.1) }
        assertThrows<IllegalArgumentException> {
            RenderFraming(RenderFrameMode.CROP, path = listOf(RenderFramePoint(2_000, 0.5, 0.5), RenderFramePoint(1_000, 0.5, 0.5)))
        }
        assertThrows<IllegalArgumentException> { ShortsFilterGraph.build(RenderFraming.FIT_BARS, 1081, 1920, null) }
        assertEquals(RenderFrameMode.FIT_BARS, ClipRenderRequest("s", 0, 1).framing.mode)
    }
}
