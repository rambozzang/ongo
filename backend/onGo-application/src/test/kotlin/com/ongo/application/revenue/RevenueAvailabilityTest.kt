package com.ongo.application.revenue

import com.ongo.domain.analytics.RevenueStatus
import com.ongo.domain.revenue.PlatformRevenueStatusCount
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "수익 0 원"의 원인을 사용자에게 정직하게 말하는지 고정한다.
 *
 * 재연동이 필요한 것과, 며칠 기다리면 되는 것과, 애초에 수집하지 않는 것은 전혀 다른
 * 상황이다. 전부 "0 원"으로 보여 주면 크리에이터는 광고 수익이 정말 0 이라고 읽는다.
 */
class RevenueAvailabilityTest {

    private fun count(platform: String, status: RevenueStatus, rows: Long = 1) =
        PlatformRevenueStatusCount(platform, status.name, rows)

    @Test
    @DisplayName("측정된 행이 하나라도 있으면 사용 가능하다")
    fun measuredRowsMakeItAvailable() {
        val result = RevenueAvailability.evaluate(
            listOf(
                count("YOUTUBE", RevenueStatus.MEASURED, rows = 3),
                count("TIKTOK", RevenueStatus.UNSUPPORTED, rows = 30),
            ),
        )

        assertTrue(result.available)
        assertNull(result.reason)
    }

    /**
     * **TikTok 만 있는 사용자.** `revenue_micro` 기본값 0 이 30일치 쌓여 있어도
     * 그건 측정값이 아니다.
     */
    @Test
    @DisplayName("TikTok 만 연동하면 수익 가용이 아니다")
    fun tiktokOnlyIsNotAvailable() {
        val result = RevenueAvailability.evaluate(
            listOf(count("TIKTOK", RevenueStatus.UNSUPPORTED, rows = 30)),
        )

        assertFalse(result.available)
        assertTrue(result.reason!!.contains("자동 수집하지 않습니다"))
        assertTrue(result.reason!!.contains("브랜드딜"))
    }

    @Test
    @DisplayName("지원하지 않는 플랫폼의 실측 표식만으로 수익을 열지 않는다")
    fun unsupportedPlatformMeasuredRowDoesNotOpenRevenue() {
        val result = RevenueAvailability.evaluate(
            listOf(count("TIKTOK", RevenueStatus.MEASURED, rows = 1)),
        )

        assertFalse(result.available)
        assertTrue(result.reason!!.contains("자동 수집하지 않습니다"))
    }

    @Test
    @DisplayName("YouTube의 미조회 기본값을 미지원으로 안내하지 않는다")
    fun youtubeUnsupportedDefaultMeansNotQueried() {
        val result = RevenueAvailability.evaluate(
            listOf(count("YOUTUBE", RevenueStatus.UNSUPPORTED, rows = 30)),
        )

        assertFalse(result.available)
        assertTrue(result.reason!!.contains("아직 조회하지 않았습니다"))
        assertFalse(result.reason.contains("자동 수집하지 않습니다"))
        assertTrue(result.reconnectRequired)
    }

    /**
     * **수익을 수집하지 않는 플랫폼의 MEASURED 는 근거가 아니다.**
     *
     * 합산 SQL 도 같은 플랫폼 집합만 더하므로, 이 행은 금액에도 들어가지 않는다.
     * 한쪽만 걸면 "금액은 0 이 아닌데 화면은 수집하지 않는다고 말하는" 모순이 난다.
     */
    @Test
    @DisplayName("수익 미수집 플랫폼의 MEASURED 는 가용으로 보지 않는다")
    fun measuredOnNonMeasurablePlatformIsIgnored() {
        val result = RevenueAvailability.evaluate(
            listOf(count("TIKTOK", RevenueStatus.MEASURED, rows = 5)),
        )

        assertFalse(result.available)
        assertTrue(result.reason!!.contains("자동 수집하지 않습니다"))
        assertFalse(result.reconnectRequired)
    }

    @Test
    @DisplayName("YouTube 가 섞여 있어도 미수집 플랫폼의 MEASURED 로 열리지 않는다")
    fun nonMeasurableMeasuredDoesNotUnlockAlongsideYouTube() {
        val result = RevenueAvailability.evaluate(
            listOf(
                count("TIKTOK", RevenueStatus.MEASURED, rows = 5),
                count("YOUTUBE", RevenueStatus.PERMISSION_REQUIRED, rows = 30),
            ),
        )

        assertFalse(result.available)
        assertTrue(result.reconnectRequired)
    }

    /** 재연동 안내는 사용자가 실제로 조치할 수 있는 상태에서만 켠다. */
    @Test
    @DisplayName("reconnectRequired 는 권한 부족과 미조회에서만 true 다")
    fun reconnectFlagIsOnlyForActionableStates() {
        fun flagFor(status: RevenueStatus) =
            RevenueAvailability.evaluate(listOf(count("YOUTUBE", status))).reconnectRequired

        assertTrue(flagFor(RevenueStatus.PERMISSION_REQUIRED))
        assertTrue(flagFor(RevenueStatus.UNSUPPORTED))
        assertFalse(flagFor(RevenueStatus.PENDING))
        assertFalse(flagFor(RevenueStatus.ERROR))
        assertFalse(flagFor(RevenueStatus.MEASURED))
        assertFalse(RevenueAvailability.evaluate(emptyList()).reconnectRequired)
        assertFalse(
            RevenueAvailability.evaluate(listOf(count("TIKTOK", RevenueStatus.UNSUPPORTED)))
                .reconnectRequired,
        )
    }

    /** 데이터가 아예 없는 것은 "미지원"이 아니다. 사용자가 할 일이 다르다. */
    @Test
    @DisplayName("행이 하나도 없으면 미지원이 아니라 집계 대기로 안내한다")
    fun noRowsMeansNoDataYet() {
        val result = RevenueAvailability.evaluate(emptyList())

        assertFalse(result.available)
        assertTrue(result.reason!!.contains("수집된 성과 데이터가 없습니다"))
        assertFalse(result.reason!!.contains("자동 수집하지 않습니다"))
    }

    @Test
    @DisplayName("행 수가 0 인 집계는 없는 것으로 본다")
    fun zeroRowCountsAreIgnored() {
        val result = RevenueAvailability.evaluate(
            listOf(count("YOUTUBE", RevenueStatus.MEASURED, rows = 0)),
        )

        assertFalse(result.available)
        assertTrue(result.reason!!.contains("수집된 성과 데이터가 없습니다"))
    }

    /** 사용자가 직접 고칠 수 있는 상황을 먼저 알린다. */
    @Test
    @DisplayName("권한 부족이 집계 대기보다 먼저 안내된다")
    fun permissionRequiredOutranksPending() {
        val result = RevenueAvailability.evaluate(
            listOf(
                count("YOUTUBE", RevenueStatus.PENDING, rows = 10),
                count("YOUTUBE", RevenueStatus.PERMISSION_REQUIRED, rows = 20),
            ),
        )

        assertFalse(result.available)
        assertTrue(result.reason!!.contains("다시 연동"))
        assertTrue(result.reconnectRequired)
    }

    @Test
    @DisplayName("확정 지연은 재연동이 아니라 기다리라고 안내한다")
    fun pendingAsksToWait() {
        val result = RevenueAvailability.evaluate(
            listOf(count("YOUTUBE", RevenueStatus.PENDING, rows = 30)),
        )

        assertFalse(result.available)
        assertTrue(result.reason!!.contains("확정"))
        assertFalse(result.reason!!.contains("다시 연동"))
    }

    @Test
    @DisplayName("조회 오류는 오류로 안내한다")
    fun errorIsReportedAsError() {
        val result = RevenueAvailability.evaluate(
            listOf(count("YOUTUBE", RevenueStatus.ERROR, rows = 30)),
        )

        assertFalse(result.available)
        assertTrue(result.reason!!.contains("오류"))
    }

    /** 측정값이 있으면 다른 상태가 섞여 있어도 보여 준다. 일부라도 진짜 수익이다. */
    @Test
    @DisplayName("측정과 권한 부족이 섞이면 측정을 우선한다")
    fun measuredWinsOverEverything() {
        val result = RevenueAvailability.evaluate(
            listOf(
                count("YOUTUBE", RevenueStatus.PERMISSION_REQUIRED, rows = 20),
                count("YOUTUBE", RevenueStatus.MEASURED, rows = 1),
            ),
        )

        assertTrue(result.available)
    }

    @Test
    @DisplayName("모든 상태에 사유 문구가 있다")
    fun everyUnavailableStateExplainsItself() {
        RevenueStatus.entries
            .filter { it != RevenueStatus.MEASURED }
            .forEach { status ->
                val result = RevenueAvailability.evaluate(listOf(count("YOUTUBE", status, rows = 1)))
                assertFalse(result.available, "$status 는 가용이 아니어야 한다")
                assertEquals(false, result.reason.isNullOrBlank(), "$status 사유가 비었다")
            }
    }
}
