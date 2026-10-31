package com.ongo.application.ugc.shorts

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ugc.shorts.ShortsClipEditPolicy.LineEdit
import com.ongo.application.ugc.shorts.ShortsRenderSpecBuilder.SubtitleLine
import com.ongo.common.exception.BusinessException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 클립 편집 규칙. 단어 시각은 측정값이라, 구간을 옮기면 같이 옮기고 문구가 바뀌면 버린다 —
 * 어긋난 강조를 렌더하지 않게 하는 것이 이 시험의 핵심이다.
 */
class ShortsClipEditPolicyTest {

    private fun words(vararg pairs: Pair<Long, String>) = pairs.map { (at, text) -> CaptionWord(at, at + 400, text) }

    /** 클립 기준: 0~2초 "안녕하세요 여러분", 3~5초 "오늘은 이야기", 9~10초 "끝" */
    private val lines = listOf(
        SubtitleLine(0, 2_000, "안녕하세요 여러분", words(0L to "안녕하세요", 1_000L to "여러분")),
        SubtitleLine(3_000, 5_000, "오늘은 이야기", words(3_000L to "오늘은", 4_000L to "이야기")),
        SubtitleLine(9_000, 10_000, "끝", words(9_000L to "끝")),
    )

    @Nested
    inner class 구간_다듬기 {

        @Test
        fun `앞을 1초 잘라 내면 자막과 단어 시각이 모두 1초 당겨진다`() {
            // 원본 기준 10_000~20_000 이던 클립을 11_000~20_000 으로
            val shifted = ShortsClipEditPolicy.shiftSubtitles(lines, oldStartMs = 10_000, newStartMs = 11_000, newEndMs = 20_000)

            // 첫 줄은 0~1초로 잘리고, 잘려 나간 "안녕하세요"(0~0.4초 → -1~-0.6초)는 빠진다
            assertEquals(SubtitleLine(0, 1_000, "안녕하세요 여러분", words(0L to "여러분")), shifted[0])
            assertEquals(SubtitleLine(2_000, 4_000, "오늘은 이야기", words(2_000L to "오늘은", 3_000L to "이야기")), shifted[1])
            assertEquals(8_000, shifted[2].startMs)
        }

        @Test
        fun `뒤를 잘라 내면 범위 밖 줄은 버리고 경계에 걸린 줄은 끝을 맞춘다`() {
            // 10_000~14_500 으로: 길이 4.5초
            val shifted = ShortsClipEditPolicy.shiftSubtitles(lines, 10_000, 10_000, 14_500)

            assertEquals(2, shifted.size)
            assertEquals(4_500, shifted[1].endMs)
            // 4.0~4.4초 "이야기"는 새 끝(4.5초) 안이라 남는다
            assertEquals(listOf("오늘은", "이야기"), shifted[1].words.map { it.text })
        }

        @Test
        fun `잘려서 0점2초보다 짧아진 줄은 버린다`() {
            val shifted = ShortsClipEditPolicy.shiftSubtitles(lines, 10_000, 11_900, 20_000)

            assertEquals(listOf("오늘은 이야기", "끝"), shifted.map { it.text })
        }

        @Test
        fun `앞으로 늘리면 자막은 그대로 뒤로 밀리고, 비어 있는 앞부분을 알린다 — 자막을 지어내지 않는다`() {
            val shifted = ShortsClipEditPolicy.shiftSubtitles(lines, 10_000, 7_000, 20_000)

            assertEquals(3_000, shifted[0].startMs)
            assertEquals(3_000, shifted[0].words.first().startMs)
            assertTrue(ShortsClipEditPolicy.hasUncoveredSpan(shifted, 13_000))
            assertFalse(ShortsClipEditPolicy.hasUncoveredSpan(lines, 10_500))
        }

        @Test
        fun `원래 자막이 없던 클립은 비어 있다고 알리지 않는다`() {
            assertFalse(ShortsClipEditPolicy.hasUncoveredSpan(emptyList(), 30_000))
        }

        @Test
        fun `구간은 원본 길이 안에서 5~180초`() {
            assertFailsWith<BusinessException> { ShortsClipEditPolicy.validateRange(-1, 10_000, null) }
            assertFailsWith<BusinessException> { ShortsClipEditPolicy.validateRange(10_000, 10_000, null) }
            assertFailsWith<BusinessException> { ShortsClipEditPolicy.validateRange(0, 4_999, null) }
            assertFailsWith<BusinessException> { ShortsClipEditPolicy.validateRange(0, 180_001, null) }
            assertFailsWith<BusinessException> { ShortsClipEditPolicy.validateRange(50_000, 70_000, sourceDurationMs = 60_000) }
            ShortsClipEditPolicy.validateRange(50_000, 60_000, sourceDurationMs = 60_000)
        }
    }

    @Nested
    inner class 자막_줄_수정 {

        @Test
        fun `문구를 고친 줄은 단어 시각을 버리고, 손대지 않은 줄은 이어받는다`() {
            val saved = ShortsClipEditPolicy.applyLineEdits(
                listOf(
                    LineEdit(0, 2_000, "안녕하세요 여러분"), // 그대로
                    LineEdit(3_000, 5_000, "오늘의 이야기"), // 문구 수정
                    LineEdit(9_000, 10_000, "끝"),
                ),
                lines,
                clipLengthMs = 10_000,
            )

            assertEquals(2, saved[0].words.size)
            assertTrue(saved[1].words.isEmpty())
            assertEquals(1, saved[2].words.size)
        }

        @Test
        fun `시각만 바꿔도 단어 시각을 버린다`() {
            val saved = ShortsClipEditPolicy.applyLineEdits(listOf(LineEdit(0, 2_500, "안녕하세요 여러분")), lines, 10_000)

            assertTrue(saved.single().words.isEmpty())
        }

        @Test
        fun `앞뒤 공백만 다른 문구는 같은 문구로 본다`() {
            val saved = ShortsClipEditPolicy.applyLineEdits(listOf(LineEdit(9_000, 10_000, "  끝 ")), lines, 10_000)

            assertEquals("끝", saved.single().text)
            assertEquals(1, saved.single().words.size)
        }

        @Test
        fun `빈 문구·범위 밖·겹침·너무 긴 문구는 거절한다`() {
            val cases = listOf(
                listOf(LineEdit(0, 1_000, "   ")),
                listOf(LineEdit(0, 11_000, "넘침")),
                listOf(LineEdit(2_000, 1_000, "거꾸로")),
                listOf(LineEdit(0, 2_000, "앞"), LineEdit(1_500, 3_000, "겹침")),
                listOf(LineEdit(0, 1_000, "가".repeat(ShortsClipEditPolicy.MAX_SUBTITLE_TEXT + 1))),
            )
            cases.forEach { edits ->
                val ex = assertFailsWith<BusinessException> { ShortsClipEditPolicy.applyLineEdits(edits, lines, 10_000) }
                assertEquals("SHORTS_CLIP_EDIT_INVALID", ex.code)
            }
        }

        @Test
        fun `줄을 모두 지우면 빈 자막으로 저장한다`() {
            assertEquals("[]", ShortsClipEditPolicy.toJson(ShortsClipEditPolicy.applyLineEdits(emptyList(), lines, 10_000)))
        }
    }

    @Test
    fun `공유 형식으로 저장하고 렌더 지시서 파서가 그대로 읽는다`() {
        val json = ShortsClipEditPolicy.toJson(listOf(lines[0], SubtitleLine(3_000, 5_000, "단어 없음")))

        val nodes = jacksonObjectMapper().readTree(json)
        assertEquals(listOf("startMs", "endMs", "text", "words"), nodes[0].fieldNames().asSequence().toList())
        assertFalse(nodes[1].has("words")) // 단어가 없으면 키를 넣지 않는다
        assertEquals(listOf(lines[0], SubtitleLine(3_000, 5_000, "단어 없음")), ShortsRenderSpecBuilder().parseSubtitles(json))
    }

    @Test
    fun `제목은 300자, 설명은 2200자까지`() {
        ShortsClipEditPolicy.validateText("가".repeat(300), "나".repeat(2_200))
        assertFailsWith<BusinessException> { ShortsClipEditPolicy.validateText("가".repeat(301), null) }
        assertFailsWith<BusinessException> { ShortsClipEditPolicy.validateText(null, "나".repeat(2_201)) }
    }
}
