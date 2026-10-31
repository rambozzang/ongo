package com.ongo.application.ugc.shorts

import com.ongo.domain.ugc.shorts.FrameSheet
import com.ongo.domain.ugc.shorts.RenderFrameMode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 장면 격자의 인물 위치가 원본 안의 9:16 이동 크롭이 되는지, 부족하면 적용하지 않는지 고정한다. */
class ShortsSpeakerTrackingTest {

    private val sheet = FrameSheet(
        image = byteArrayOf(1), mimeType = "image/jpeg", columns = 4, rows = 3,
        tileTimesMs = (0 until 12).map { it * 2_500L }, sourceWidth = 1920, sourceHeight = 1080,
    )

    private fun tile(i: Int, x: Double, y: Double = 0.3) = SpeakerTrackResult.Tile(i, true, x, y)

    @Test
    fun `인물을 찾은 칸이 절반 미만이면 적용하지 않는다`() {
        val result = SpeakerTrackResult((1..5).map { tile(it, 0.5) } + (6..12).map { SpeakerTrackResult.Tile(it, false) })

        assertNull(ShortsSpeakerTracking.toTrack(sheet, result))
    }

    @Test
    fun `범위 밖 좌표와 없는 칸 번호는 찾지 못한 것으로 본다`() {
        val result = SpeakerTrackResult(
            (1..5).map { tile(it, 0.5) } + listOf(tile(6, 1.7), tile(7, -0.1), tile(13, 0.5), tile(0, 0.5)),
        )

        assertNull(ShortsSpeakerTracking.toTrack(sheet, result))
    }

    @Test
    fun `두 사람이 번갈아 말하면 그 자리에 머물다 짧게 옮기는 9대16 크롭을 만든다`() {
        // 1~6칸은 왼쪽 사람, 7~12칸은 오른쪽 사람
        val result = SpeakerTrackResult((1..6).map { tile(it, 0.25) } + (7..12).map { tile(it, 0.8) })

        val track = assertNotNull(ShortsSpeakerTracking.toTrack(sheet, result))
        val crop = track.crop

        assertEquals(12, track.foundTiles)
        assertEquals(RenderFrameMode.CROP, crop.mode)
        assertEquals(1080, crop.height)
        assertEquals(606, crop.width)
        assertTrue(crop.keyframes.all { it.x >= 0 && it.x + it.width <= 1920 && it.y >= 0 && it.y + it.height <= 1080 })

        // 옮기기 직전(7칸 시각 - 350ms)까지는 왼쪽, 7칸 시각에는 오른쪽
        val switchAt = 6 * 2_500L
        val hold = crop.keyframes.single { it.atMs == switchAt - ShortsSpeakerTracking.MOVE_MS }
        val moved = crop.keyframes.single { it.atMs == switchAt }
        assertTrue(hold.x + hold.width / 2 < 960, "옮기기 직전에는 왼쪽에 머물러야 한다: $hold")
        assertTrue(moved.x + moved.width / 2 > 960, "옮긴 뒤에는 오른쪽이어야 한다: $moved")

        // 같은 자리의 잔떨림 지점은 정리되어 지점 수가 칸 수보다 적다
        assertTrue(crop.keyframes.size < 12, "${crop.keyframes.size}")
    }

    @Test
    fun `프롬프트는 칸 수와 격자 크기와 칸 순서와 지어내지 말라는 지시를 담는다`() {
        val prompt = ShortsSpeakerTracking.prompt(sheet)

        assertTrue(prompt.contains("12개") && prompt.contains("4열 3행"), prompt)
        assertTrue(prompt.contains("왼쪽 위가 1"), prompt)
        assertTrue(prompt.contains("추측해서 채우지 마세요"), prompt)
    }
}
