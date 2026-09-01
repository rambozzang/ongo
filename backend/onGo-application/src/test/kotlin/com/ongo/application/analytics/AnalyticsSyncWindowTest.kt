package com.ongo.application.analytics

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 어떤 날짜를 다시 조회할지의 계약.
 *
 * ## 고치는 결함
 *
 * 예전에는 `MAX(date) + 1` 부터 앞으로만 훑었다. 그래서 날짜 D 조회가 실패했는데 같은
 * 실행의 뒤쪽 조회가 성공해 D+1 이 저장되면, 다음 실행의 시작점이 D 를 지나가 **D 는
 * 영원히 다시 조회되지 않았다.** 수익은 분석 행이 있어야 갱신되므로(`updateRevenue` 는
 * 행을 만들지 않는다) 그 날짜의 수익도 함께 영구 유실됐다.
 */
class AnalyticsSyncWindowTest {

    private val today = LocalDate.of(2026, 8, 28)
    private fun d(day: Int) = LocalDate.of(2026, 8, day)

    /** **이 테스트가 결함 그 자체다.** */
    @Test
    @DisplayName("최신일보다 앞선 중간 누락을 다시 고른다")
    fun picksGapBeforeWatermark() {
        // 8/20~8/27 수집됐는데 8/24 만 비어 있다. MAX(date) 는 8/27 이다.
        val existing = (20..27).map(::d).filter { it != d(24) }.toSet()

        val dates = AnalyticsSyncWindow.datesToSync(today, latestDate = d(27), existingDates = existing)

        assertTrue(d(24) in dates, "최신일 이전의 빈 날짜를 건너뛰었습니다: $dates")
    }

    /** 여러 개가 비어도 전부 후보에 들어간다. */
    @Test
    @DisplayName("중간 누락이 여러 개면 모두 고른다")
    fun picksEveryGap() {
        val existing = (20..27).map(::d).filter { it != d(22) && it != d(25) }.toSet()

        val dates = AnalyticsSyncWindow.datesToSync(today, d(27), existing)

        assertTrue(d(22) in dates && d(25) in dates, dates.toString())
    }

    /** 재실행 멱등: 모두 채워졌으면 더 부르지 않는다. */
    @Test
    @DisplayName("빈 날짜가 없으면 아무것도 조회하지 않는다")
    fun idempotentWhenComplete() {
        val existing = (1..27).map(::d).toSet()

        assertEquals(emptyList(), AnalyticsSyncWindow.datesToSync(today, d(27), existing))
    }

    /** 한 번 채운 날짜는 다음 실행에서 다시 고르지 않는다. */
    @Test
    @DisplayName("채워진 뒤에는 그 날짜를 다시 고르지 않는다")
    fun doesNotRefetchFilledDates() {
        val before = (20..27).map(::d).filter { it != d(24) }.toSet()
        val first = AnalyticsSyncWindow.datesToSync(today, d(27), before)
        assertTrue(d(24) in first)

        val after = before + d(24)
        val second = AnalyticsSyncWindow.datesToSync(today, d(27), after)

        assertTrue(d(24) !in second, "이미 채운 날짜를 또 부릅니다: $second")
    }

    /** 창 밖은 무한 재시도하지 않는다. */
    @Test
    @DisplayName("30일 창 밖의 누락은 조회하지 않는다")
    fun ignoresGapsOutsideWindow() {
        val windowStart = today.minusDays(AnalyticsSyncWindow.GAP_WINDOW_DAYS.toLong())
        // 창 안은 전부 채워져 있고, 창보다 오래된 날짜만 비어 있다.
        val existing = generateSequence(windowStart) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today.minusDays(1)) }
            .toSet()

        val dates = AnalyticsSyncWindow.datesToSync(today, today.minusDays(1), existing)

        assertEquals(emptyList(), dates)
        assertTrue(dates.none { it.isBefore(windowStart) })
    }

    @Test
    @DisplayName("조회 날짜는 항상 창 안이고 오늘을 포함하지 않는다")
    fun staysInsideWindow() {
        val windowStart = today.minusDays(AnalyticsSyncWindow.GAP_WINDOW_DAYS.toLong())

        val dates = AnalyticsSyncWindow.datesToSync(today, latestDate = d(1), existingDates = emptySet())

        assertTrue(dates.all { !it.isBefore(windowStart) }, dates.toString())
        assertTrue(dates.none { it.isAfter(today.minusDays(1)) }, "오늘 이후를 조회합니다: $dates")
    }

    /** provider 호출 상한. 오늘 조회는 이 상한과 별개로 한 번 더 붙는다. */
    @Test
    @DisplayName("한 실행·업로드당 최대 7개까지만 고른다")
    fun capsProviderCalls() {
        // 창 전체가 비어 있는 장기 미동기 업로드.
        val dates = AnalyticsSyncWindow.datesToSync(today, latestDate = d(1), existingDates = emptySet())

        assertEquals(AnalyticsSyncWindow.MAX_PROVIDER_CALLS_PER_UPLOAD, dates.size)
        assertEquals(7, dates.size)
    }

    /** 수익 창에 가까운 날짜가 더 급하고 제공자 쪽에도 더 잘 남아 있다. */
    @Test
    @DisplayName("최신 누락부터 채운다")
    fun fillsNewestFirst() {
        val dates = AnalyticsSyncWindow.datesToSync(today, d(1), emptySet())

        assertEquals(today.minusDays(1), dates.first())
        assertEquals(dates.sortedDescending(), dates)
    }

    /**
     * 한 번도 수집한 적 없는 업로드는 종전대로 어제 하루만 본다. 게시 전 날짜를 30일치
     * 두드리면 호출만 낭비된다.
     */
    @Test
    @DisplayName("신규 업로드는 어제 하루만 조회한다")
    fun newUploadChecksYesterdayOnly() {
        val dates = AnalyticsSyncWindow.datesToSync(today, latestDate = null, existingDates = emptySet())

        assertEquals(listOf(today.minusDays(1)), dates)
    }

    /** 수집 이력이 전부 창보다 오래됐으면 창을 다시 채운다. */
    @Test
    @DisplayName("장기 미동기 업로드는 창 전체를 후보로 삼는다")
    fun longDormantUploadRefillsWindow() {
        val dates = AnalyticsSyncWindow.datesToSync(today, latestDate = d(1), existingDates = setOf(d(1)))

        assertEquals(7, dates.size)
        assertTrue(dates.contains(today.minusDays(1)))
    }

    /** 수집 구간보다 앞선(게시 전) 날짜는 후보로 잡지 않는다 — 영구 재시도 방지. */
    @Test
    @DisplayName("첫 수집일보다 앞선 날짜는 조회하지 않는다")
    fun doesNotProbeBeforeFirstKnownDate() {
        val existing = setOf(d(26), d(27))

        val dates = AnalyticsSyncWindow.datesToSync(today, d(27), existing)

        assertTrue(dates.none { it.isBefore(d(26)) }, "게시 전일 수 있는 날짜를 두드립니다: $dates")
    }
}
