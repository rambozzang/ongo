package com.ongo.application.ugc.shorts

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 한국어 쇼츠 자막이 짧은 묶음으로, 어절 경계에서만, 말과 함께 바뀌는지 고정한다. */
class KoreanCaptionLayoutTest {

    private fun words(vararg pairs: Pair<String, LongRange>) = pairs.map { (t, r) -> CaptionWord(r.first, r.last, t) }

    @Test
    fun `긴 문장은 16자 이하 묶음으로 나뉘고 각 줄은 어절 경계에서만 끊긴다`() {
        val ws = words(
            "오늘은" to 0L..400L, "제가" to 400L..700L, "직접" to 700L..1000L, "만든" to 1000L..1300L,
            "김치찌개" to 1300L..1800L, "레시피를" to 1800L..2300L, "알려드릴게요" to 2300L..3000L,
        )
        val cards = KoreanCaptionLayout.fromWords(ws, 10_000)

        assertTrue(cards.size >= 2, "한 화면에 다 들어가면 안 된다: $cards")
        cards.forEach { card ->
            val text = card.lines.joinToString(" ") { line -> line.joinToString(" ") { it.text } }
            assertTrue(KoreanCaptionLayout.displayLength(text) <= KoreanCaptionLayout.MAX_CARD_CHARS, text)
            assertTrue(card.lines.size <= 2)
            assertTrue(card.timed)
        }
        // 어절이 빠지거나 쪼개지지 않는다
        assertEquals(ws.map { it.text }, cards.flatMap { it.words }.map { it.text })
    }

    @Test
    fun `문장 끝과 말 사이 쉼에서 새 묶음을 시작한다`() {
        val cards = KoreanCaptionLayout.fromWords(
            words("진짜요?" to 0L..500L, "네" to 600L..800L, "그리고" to 2_000L..2_400L, "다음" to 2_400L..2_700L),
            5_000,
        )

        assertEquals(listOf(listOf("진짜요?"), listOf("네"), listOf("그리고", "다음")), cards.map { c -> c.words.map { it.text } })
    }

    @Test
    fun `한 줄에 넘치면 가운데에 가까운 어절 경계에서 두 줄로 나눈다`() {
        val cards = KoreanCaptionLayout.fromWords(
            words("구독과" to 0L..300L, "좋아요" to 300L..600L, "부탁드려요" to 600L..1_100L),
            5_000,
        )

        assertEquals(1, cards.size)
        assertEquals(listOf(listOf("구독과", "좋아요"), listOf("부탁드려요")), cards[0].lines.map { l -> l.map { it.text } })
    }

    @Test
    fun `너무 짧게 지나가는 묶음은 다음 묶음 전까지 최소 시간 보인다`() {
        val cards = KoreanCaptionLayout.fromWords(words("앗" to 0L..100L, "그래서" to 1_500L..1_900L), 5_000)

        assertEquals(KoreanCaptionLayout.MIN_CARD_MS, cards[0].endMs - cards[0].startMs)
        assertTrue(cards[0].endMs <= cards[1].startMs)
    }

    @Test
    fun `단어 시각이 없는 줄은 글자 수로 시각을 나누되 강조용 시각으로 표시하지 않는다`() {
        val cards = KoreanCaptionLayout.fromLines(listOf(0L..3_000L to "오늘은 제가 직접 만든 김치찌개 레시피를 알려드릴게요"), 10_000)

        assertTrue(cards.size >= 2)
        assertTrue(cards.none { it.timed })
        assertEquals(0L, cards.first().startMs)
        assertTrue(cards.last().endMs <= 3_000L + KoreanCaptionLayout.MIN_CARD_MS)
    }

    @Test
    fun `클립 길이를 넘지 않고 빈 단어와 뒤집힌 시각은 버린다`() {
        val cards = KoreanCaptionLayout.fromWords(
            words("  " to 0L..100L, "끝" to 900L..1_400L, "거꾸로" to 500L..400L),
            1_000,
        )

        assertEquals(listOf("끝"), cards.flatMap { it.words }.map { it.text })
        assertTrue(cards.all { it.endMs <= 1_000 })
    }

    @Test
    fun `영문과 숫자는 반 칸으로 센다`() {
        assertEquals(3, KoreanCaptionLayout.displayLength("안녕하"))
        assertEquals(2, KoreanCaptionLayout.displayLength("ABCD"))
        assertFalse(KoreanCaptionLayout.displayLength("2026년") > 4)
    }
}
