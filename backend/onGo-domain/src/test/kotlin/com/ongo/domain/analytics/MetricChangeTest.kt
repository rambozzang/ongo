package com.ongo.domain.analytics

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 증감률은 **정의될 때만** 숫자여야 한다.
 *
 * 예전에는 `previous = 0` 일 때 `current > 0` 이면 `100.0` 을 돌려줬다. 0 으로 나눌 수 없어
 * 정의되지 않는 자리에 **임의의 숫자**를 넣은 것이다. 그래서 첫 주에 5만 조회를 낸 채널과
 * 100 → 200 으로 는 채널이 대시보드에서 똑같이 "▲100%" 로 보였다.
 */
class MetricChangeTest {

    // ── 정의되지 않는 경우 ───────────────────────────────────────────────────

    /** **이 테스트가 수정의 핵심이다.** 100.0 은 측정값이 아니라 지어낸 값이었다. */
    @Test
    @DisplayName("이전 기간이 0이면 증가율을 만들어내지 않는다")
    fun growthFromZeroIsUndefined() {
        assertNull(MetricChange.percentChange(previous = 0, current = 50_000))
        assertNull(MetricChange.percentChange(previous = 0, current = 1))
    }

    /** 비교할 기간에 데이터가 없는 것과 "변화가 없었다"는 다른 사실이다. */
    @Test
    @DisplayName("두 기간 모두 0이면 변화 없음이 아니라 비교 불가다")
    fun zeroToZeroIsUndefinedNotFlat() {
        assertNull(MetricChange.percentChange(previous = 0, current = 0))
    }

    // ── 정의되는 경우 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("이전 기간이 있으면 증감률을 계산한다")
    fun computesChangeWhenComparable() {
        assertEquals(100.0, MetricChange.percentChange(previous = 100, current = 200))
        assertEquals(-50.0, MetricChange.percentChange(previous = 200, current = 100))
        assertEquals(0.0, MetricChange.percentChange(previous = 100, current = 100))
    }

    /** 현재가 0 이어도 이전 기간이 있으면 -100% 는 **측정된 사실**이다. */
    @Test
    @DisplayName("현재가 0이면 -100%로 계산한다 — 비교 불가가 아니다")
    fun dropToZeroIsMeasured() {
        assertEquals(-100.0, MetricChange.percentChange(previous = 500, current = 0))
    }

    // ── 문자열 표현 ──────────────────────────────────────────────────────────

    /**
     * `String.format("%.1f", null)` 은 문자열 `"null"` 을 만든다. 그 값이 AI 프롬프트에
     * 들어가면 모델이 그것을 수치로 읽고 없는 추세를 지어낸다.
     */
    @Test
    @DisplayName("비교 불가는 숫자도 null 문자열도 아닌 문장으로 표현한다")
    fun unavailableIsDescribedInWords() {
        val text = MetricChange.describePercent(null)

        assertEquals(MetricChange.UNAVAILABLE_TEXT, text)
        assertTrue("null" !in text, "문자열 'null' 이 그대로 노출된다: $text")
        assertTrue(text.none { it.isDigit() }, "비교 불가인데 숫자가 들어 있다: $text")
    }

    /** 단위를 값이 직접 들고 있어야 템플릿에 `%` 를 붙일 필요가 없다. */
    @Test
    @DisplayName("표현 가능한 값은 단위까지 포함해 문자열로 만든다")
    fun availableCarriesItsUnit() {
        assertEquals("12.3%", MetricChange.describePercent(12.34))
        assertEquals("-50.0%", MetricChange.describePercent(-50.0))
    }
}
