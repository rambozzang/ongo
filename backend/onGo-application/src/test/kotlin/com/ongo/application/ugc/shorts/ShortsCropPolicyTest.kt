package com.ongo.application.ugc.shorts

import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.RenderFrameMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 크롭 정규화가 **어떤 입력에도 늘어나지 않고 원본 밖으로 나가지 않는 9:16 창**을 돌려주는지 고정한다.
 * REFRAME 단계·편집기 저장·렌더가 모두 이 한 곳을 쓰므로 여기가 깨지면 전부 깨진다.
 */
class ShortsCropPolicyTest {

    private fun assertNineBySixteen(spec: CropSpec) {
        val ratio = spec.width.toDouble() / spec.height
        assertTrue(kotlin.math.abs(ratio - 9.0 / 16) < 0.01, "9:16 이 아니다: ${spec.width}x${spec.height}")
        assertTrue(spec.width % 2 == 0 && spec.height % 2 == 0, "짝수가 아니다: ${spec.width}x${spec.height}")
    }

    private fun assertInside(spec: CropSpec) {
        val sw = spec.sourceWidth!!
        val sh = spec.sourceHeight!!
        assertTrue(spec.x >= 0 && spec.y >= 0 && spec.x + spec.width <= sw && spec.y + spec.height <= sh, "원본 밖: $spec")
        spec.keyframes.forEach { k ->
            assertTrue(k.x >= 0 && k.y >= 0 && k.x + k.width <= sw && k.y + k.height <= sh, "지점이 원본 밖: $k")
        }
    }

    @Test
    fun `예전 LLM 상자(1080x1920 을 가로 원본에)는 원본 높이에 맞는 9대16 창으로 줄인다`() {
        val n = ShortsCropPolicy.normalize(CropSpec(10, 20, 1080, 1920), 1920, 1080)

        assertEquals(1080, n.height)
        assertEquals(606, n.width)
        assertNineBySixteen(n)
        assertInside(n)
    }

    @Test
    fun `가로로 넓은 상자는 중심을 지키며 상자를 다 담는 9대16 으로 키운다`() {
        // 중심 (1000, 540), 상자 400x200 → 높이는 400·16/9 = 711 이상
        val n = ShortsCropPolicy.normalize(CropSpec(800, 440, 400, 200), 1920, 1080)

        assertNineBySixteen(n)
        assertInside(n)
        assertTrue(n.height >= 710, "상자를 다 담지 못한다: $n")
        assertEquals(1000.0, n.x + n.width / 2.0, 2.0)
    }

    @Test
    fun `원본 밖 좌표와 음수 좌표는 원본 안으로 묶는다`() {
        assertInside(ShortsCropPolicy.normalize(CropSpec(5000, 5000, 300, 600), 1920, 1080))
        assertInside(ShortsCropPolicy.normalize(CropSpec(-400, -300, 300, 600), 1920, 1080))
    }

    @Test
    fun `지나치게 작은 상자는 화질을 위해 가장 큰 창의 절반까지만 확대한다`() {
        val n = ShortsCropPolicy.normalize(CropSpec(900, 500, 10, 10), 1920, 1080)

        assertEquals(540, n.height)
        assertNineBySixteen(n)
    }

    @Test
    fun `세로 원본에서는 너비가 원본 너비를 넘지 않는다`() {
        val n = ShortsCropPolicy.normalize(CropSpec(0, 0, 2000, 4000), 1080, 1920)

        assertTrue(n.width <= 1080 && n.height <= 1920, "$n")
        assertNineBySixteen(n)
        assertInside(n)
    }

    @Test
    fun `크기가 0 이면 가장 큰 창을 쓴다`() {
        val n = ShortsCropPolicy.normalize(CropSpec(960, 540, 0, 0), 1920, 1080)

        assertEquals(1080, n.height)
        assertInside(n)
    }

    @Test
    fun `창 이동 지점은 시각순 정렬, 같은 시각은 마지막 것, 크기는 본 창과 같게 맞춘다`() {
        val spec = CropSpec(
            0, 0, 606, 1080,
            keyframes = listOf(
                CropKeyframe(2_000, 1300, 0, 606, 1080),
                CropKeyframe(0, 0, 0, 100, 100),
                CropKeyframe(2_000, 1314, 0, 606, 1080),
                CropKeyframe(-5, 0, 0, 606, 1080),
            ),
        )
        val n = ShortsCropPolicy.normalize(spec, 1920, 1080)

        assertEquals(listOf(0L, 2_000L), n.keyframes.map { it.atMs })
        assertEquals(1314, n.keyframes[1].x)
        assertTrue(n.keyframes.all { it.width == n.width && it.height == n.height })
        assertInside(n)
    }

    @Test
    fun `거의 움직이지 않는 지점은 버리고 지점 수는 상한을 넘지 않는다`() {
        val jitter = (0 until 200).map { CropKeyframe(it * 100L, 600 + (it % 2), 0, 606, 1080) }
        val n = ShortsCropPolicy.normalize(CropSpec(600, 0, 606, 1080, keyframes = jitter), 1920, 1080)
        assertEquals(2, n.keyframes.size, "잔떨림이 지점으로 남았다: ${n.keyframes.size}")

        val moving = (0 until 200).map { CropKeyframe(it * 100L, (it * 37) % 1300, 0, 606, 1080) }
        val m = ShortsCropPolicy.normalize(CropSpec(0, 0, 606, 1080, keyframes = moving), 1920, 1080)
        assertTrue(m.keyframes.size <= 60, "${m.keyframes.size}")
        assertTrue(m.keyframes.zipWithNext().all { (a, b) -> a.atMs < b.atMs })
    }

    @Test
    fun `JSON 왕복에서 값과 모드와 원본 크기와 지점을 보존한다`() {
        val n = ShortsCropPolicy.normalize(
            CropSpec(100, 0, 606, 1080, keyframes = listOf(CropKeyframe(0, 100, 0, 606, 1080), CropKeyframe(3_000, 900, 0, 606, 1080))),
            1920, 1080,
        )

        assertEquals(n, ShortsCropPolicy.parse(ShortsCropPolicy.toJson(n)))
    }

    @Test
    fun `편집기 입력이 깨졌으면 조용히 고치지 않고 거절한다`() {
        assertEquals("SHORTS_CROP_INVALID", assertThrows<BusinessException> { ShortsCropPolicy.normalizeJson("{not-json") }.code)
        assertThrows<BusinessException> { ShortsCropPolicy.normalizeJson("""{"x":1,"y":2,"width":3}""") }
        assertThrows<BusinessException> { ShortsCropPolicy.normalizeJson("""{"x":1,"y":2,"width":3,"height":4,"mode":"STRETCH"}""") }
        assertThrows<BusinessException> { ShortsCropPolicy.normalizeJson("""{"x":1,"y":2,"width":3,"height":4,"sourceWidth":0,"sourceHeight":1080}""") }
    }

    @Test
    fun `편집기가 준 원본 크기를 기준으로 정규화한다`() {
        val json = ShortsCropPolicy.normalizeJson("""{"x":1000,"y":0,"width":1215,"height":2160}""", 3840, 2160)
        val n = ShortsCropPolicy.parse(json)!!

        assertEquals(3840, n.sourceWidth)
        assertEquals(2160, n.height)
        assertNineBySixteen(n)
        assertInside(n)
    }

    @Test
    fun `저장된 JSON 이 깨졌으면 parse 는 null — 렌더는 맞춤으로 진행한다`() {
        assertNull(ShortsCropPolicy.parse("{not-json"))
        assertNull(ShortsCropPolicy.parse(null))
        assertEquals(RenderFrameMode.FIT_BARS, ShortsCropPolicy.toFraming(null, null).mode)
    }

    @Test
    fun `크롭이 없으면 템플릿 배경 스타일로 맞추고 늘이지 않는다`() {
        assertEquals(RenderFrameMode.FIT_BLUR, ShortsCropPolicy.toFraming(null, "BLURRED").mode)
        assertEquals(RenderFrameMode.FIT_BLUR, ShortsCropPolicy.toFraming(null, "BLUR").mode)
        assertEquals(RenderFrameMode.FIT_BARS, ShortsCropPolicy.toFraming(null, "BLACK_BARS").mode)
        assertEquals(RenderFrameMode.FIT_BARS, ShortsCropPolicy.toFraming(null, "SOLID").mode)
    }

    @Test
    fun `맞춤 모드를 고른 크롭은 상자를 무시하고 그 모드로 렌더한다`() {
        val framing = ShortsCropPolicy.toFraming(CropSpec(0, 0, 10, 10, mode = RenderFrameMode.FIT_BLUR), "BLACK_BARS")

        assertEquals(RenderFrameMode.FIT_BLUR, framing.mode)
    }

    @Test
    fun `크롭 화면 구성은 중심 비율과 확대 비율과 시간별 경로를 담는다`() {
        val spec = CropSpec(
            0, 0, 606, 1080, sourceWidth = 1920, sourceHeight = 1080,
            keyframes = listOf(CropKeyframe(0, 0, 0, 606, 1080), CropKeyframe(4_000, 1314, 0, 606, 1080)),
        )
        val framing = ShortsCropPolicy.toFraming(spec, null)

        assertEquals(RenderFrameMode.CROP, framing.mode)
        assertEquals(303.0 / 1920, framing.centerX, 0.001)
        assertEquals(0.5, framing.centerY, 0.001)
        assertEquals(1.0, framing.zoom, 0.001)
        assertEquals(listOf(0L, 4_000L), framing.path.map { it.atMs })
        assertEquals((1314 + 303.0) / 1920, framing.path[1].centerX, 0.001)
    }
}
