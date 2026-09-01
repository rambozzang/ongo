package com.ongo.application.revenue

import com.ongo.common.enums.AuthProvider
import com.ongo.domain.revenue.CpmRpmRaw
import com.ongo.domain.revenue.RevenueRepository
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CPM/RPM 이 **분모가 없을 때 0원으로 위장하지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * val cpm = if (raw.impressions > 0) (revenueActual / raw.impressions) * 1000 else 0.0
 * val rpm = if (raw.views > 0) (revenueActual / raw.views) * 1000 else 0.0
 * ```
 *
 * 노출이나 조회가 0 이면 나눌 것이 없어 단가가 성립하지 않는다. 그런데 그 자리에
 * `0.0` 이 들어갔고 `RevenueView` 는 **"₩0.00"** 을 그렸다 — 재지 않았을 뿐인데
 * "이 플랫폼은 수익성이 0" 이라는 관측이 된다.
 *
 * 노출은 YouTube 만 주고 조회는 대부분의 플랫폼이 준다. 그래서 두 분모는 서로
 * 독립적으로 비어 있을 수 있고, **한쪽이 없다고 다른 쪽까지 버리면 안 된다.**
 */
class CpmRpmMeasurementTest {

    private val revenueRepository = mockk<RevenueRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val useCase = RevenueUseCase(revenueRepository, userRepository)

    private val userId = 1L

    private fun givenRows(vararg rows: CpmRpmRaw) {
        every { userRepository.findById(userId) } returns User(
            id = userId,
            email = "creator@example.com",
            name = "Creator",
            provider = AuthProvider.GOOGLE,
            providerId = "google-1",
        )
        every { revenueRepository.getCpmRpmByPlatform(userId, any(), any()) } returns rows.toList()
    }

    /** 수익 1,000원 = 1,000,000,000 micro. */
    private fun row(
        platform: String = "YOUTUBE",
        impressions: Long,
        views: Long,
        revenueMicro: Long = 1_000_000_000L,
    ) = CpmRpmRaw(platform = platform, impressions = impressions, views = views, revenueMicro = revenueMicro)

    // ── 분모 하나가 없어도 다른 하나는 살린다 ────────────────────────────────

    /** **이 케이스가 "CPM ₩0.00" 을 만들던 자리다.** */
    @Test
    @DisplayName("노출수가 0이면 CPM은 측정 불가이고 RPM은 계산된다")
    fun zeroImpressionsMakesOnlyCpmUnavailable() {
        givenRows(row(impressions = 0, views = 2_000))

        val item = useCase.getCpmRpm(userId, 30).platforms.single()

        assertNull(item.cpm, "노출이 없는데 CPM 을 숫자로 만들었다")
        // 1,000원 / 2,000조회 * 1,000 = 500원
        assertEquals(500.0, item.rpm, "CPM 이 없다고 RPM 까지 버렸다")
        assertTrue("cpm" in item.unavailableMetrics)
        assertTrue("rpm" !in item.unavailableMetrics)
    }

    @Test
    @DisplayName("조회수가 0이면 RPM은 측정 불가이고 CPM은 계산된다")
    fun zeroViewsMakesOnlyRpmUnavailable() {
        givenRows(row(impressions = 4_000, views = 0))

        val item = useCase.getCpmRpm(userId, 30).platforms.single()

        assertNull(item.rpm, "조회가 없는데 RPM 을 숫자로 만들었다")
        // 1,000원 / 4,000노출 * 1,000 = 250원
        assertEquals(250.0, item.cpm, "RPM 이 없다고 CPM 까지 버렸다")
        assertTrue("rpm" in item.unavailableMetrics)
        assertTrue("cpm" !in item.unavailableMetrics)
    }

    @Test
    @DisplayName("분모가 둘 다 0이면 두 단가 모두 측정 불가다")
    fun bothDenominatorsZeroMakesBothUnavailable() {
        givenRows(row(impressions = 0, views = 0, revenueMicro = 5_000_000_000L))

        val item = useCase.getCpmRpm(userId, 30).platforms.single()

        assertNull(item.cpm)
        assertNull(item.rpm)
        assertEquals(setOf("cpm", "rpm"), item.unavailableMetrics.keys)
        // 행 자체는 남는다. 수익은 실제로 관측됐고 화면은 그것을 보여줘야 한다.
        assertEquals(5_000_000_000L, item.revenueMicro)
    }

    // ── 측정된 0 은 보존 ─────────────────────────────────────────────────────

    /**
     * **분모가 양수인데 수익이 0 이면 그 0 은 실측이다.** 노출·조회는 났는데 수익이
     * 붙지 않았다는 뜻이므로 측정 불가로 감추면 실제 관찰을 잃는다.
     */
    @Test
    @DisplayName("수익이 0이어도 분모가 양수면 0원 단가를 보존한다")
    fun measuredZeroUnitPriceIsPreserved() {
        givenRows(row(impressions = 4_000, views = 2_000, revenueMicro = 0L))

        val item = useCase.getCpmRpm(userId, 30).platforms.single()

        assertEquals(0.0, item.cpm, "측정된 0원 CPM 을 측정 불가로 감췄다")
        assertEquals(0.0, item.rpm, "측정된 0원 RPM 을 측정 불가로 감췄다")
        assertTrue(item.unavailableMetrics.isEmpty())
    }

    @Test
    @DisplayName("분모가 둘 다 양수면 두 단가를 모두 계산한다")
    fun bothDenominatorsPositiveComputesBoth() {
        givenRows(row(impressions = 4_000, views = 2_000))

        val item = useCase.getCpmRpm(userId, 30).platforms.single()

        assertEquals(250.0, item.cpm)
        assertEquals(500.0, item.rpm)
        assertTrue(item.unavailableMetrics.isEmpty())
    }

    // ── null 과 사유가 어긋나지 않는다 ───────────────────────────────────────

    /**
     * 단가와 사유를 따로 만들면 `cpm = null` 인데 사유가 비어 있는 상태가 생기고,
     * 화면은 이유 없이 빈 칸만 그린다. 둘은 항상 같이 움직여야 한다.
     */
    @Test
    @DisplayName("null인 단가는 반드시 사유를 갖고, 사유가 있으면 반드시 null이다")
    fun nullAndReasonAlwaysAgree() {
        givenRows(
            row(platform = "YOUTUBE", impressions = 4_000, views = 2_000),
            row(platform = "TIKTOK", impressions = 0, views = 2_000),
            row(platform = "INSTAGRAM", impressions = 4_000, views = 0),
            row(platform = "FACEBOOK", impressions = 0, views = 0),
        )

        useCase.getCpmRpm(userId, 30).platforms.forEach { item ->
            assertEquals(
                item.cpm == null, "cpm" in item.unavailableMetrics,
                "${item.platform}: cpm 과 사유가 어긋난다",
            )
            assertEquals(
                item.rpm == null, "rpm" in item.unavailableMetrics,
                "${item.platform}: rpm 과 사유가 어긋난다",
            )
        }
    }

    /** 사유는 숫자가 아니라 문장이어야 한다. 숫자를 넣으면 그것이 단가로 읽힌다. */
    @Test
    @DisplayName("측정 불가 사유에 숫자가 들어가지 않는다")
    fun unavailableReasonsAreNotNumbers() {
        listOf(RevenueUseCase.CPM_UNAVAILABLE, RevenueUseCase.RPM_UNAVAILABLE).forEach { reason ->
            assertTrue(reason.isNotBlank())
            assertTrue(!Regex("[0-9]").containsMatchIn(reason), "사유에 숫자가 있다: $reason")
        }
    }

    @Test
    @DisplayName("사유는 어느 분모가 비었는지 구분해서 알려준다")
    fun reasonsDistinguishWhichDenominatorIsMissing() {
        givenRows(row(impressions = 0, views = 0))

        val item = useCase.getCpmRpm(userId, 30).platforms.single()

        assertEquals(RevenueUseCase.CPM_UNAVAILABLE, item.unavailableMetrics["cpm"])
        assertEquals(RevenueUseCase.RPM_UNAVAILABLE, item.unavailableMetrics["rpm"])
    }

    // ── 플랫폼 행이 없는 경우와 구분 ─────────────────────────────────────────

    /**
     * 측정된 수익 행이 하나도 없으면 **행 자체가 없다.** 단가 하나를 못 낸 것과는
     * 다른 상태이고, 화면도 다르게 그려야 한다.
     */
    @Test
    @DisplayName("측정 행이 없으면 빈 목록이지 0원 행이 아니다")
    fun noMeasuredRowsYieldsEmptyList() {
        givenRows()

        val response = useCase.getCpmRpm(userId, 30)

        assertTrue(response.platforms.isEmpty(), "0원 행을 지어냈다")
    }

    /** 기존 플랫폼 수익 가용성·재연동 안내는 그대로 실려야 한다. */
    @Test
    @DisplayName("플랫폼 수익 가용성 안내를 계속 함께 내려보낸다")
    fun availabilityBannerIsStillCarried() {
        givenRows(row(impressions = 4_000, views = 2_000))

        val response = useCase.getCpmRpm(userId, 30)

        // 값 자체는 RevenueAvailability 가 정하므로 필드 존재와 일관성만 본다.
        assertEquals(
            response.platformRevenueAvailable,
            response.platformRevenueUnavailableReason == null,
            "가용 여부와 사유가 어긋난다",
        )
    }
}
