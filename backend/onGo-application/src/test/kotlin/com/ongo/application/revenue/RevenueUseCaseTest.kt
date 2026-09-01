package com.ongo.application.revenue

import com.ongo.common.enums.AuthProvider
import com.ongo.domain.analytics.RevenueStatus
import com.ongo.domain.revenue.PlatformRevenue
import com.ongo.domain.revenue.PlatformRevenueStatusCount
import com.ongo.domain.revenue.RevenueRepository
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RevenueUseCaseTest {
    private val revenueRepository = mockk<RevenueRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val useCase = RevenueUseCase(revenueRepository, userRepository)

    @org.junit.jupiter.api.Test
    fun `플랫폼 광고 수익을 수집하지 않으면 정상 수익처럼 표시하지 않는다`() {
        every { userRepository.findById(1L) } returns User(
            id = 1L,
            email = "creator@example.com",
            name = "Creator",
            provider = AuthProvider.GOOGLE,
            providerId = "google-1",
        )
        every { revenueRepository.getPlatformRevenue(1L, any(), any()) } returns
            listOf(PlatformRevenue(platform = "YOUTUBE", totalRevenueMicro = 0L))

        val response = useCase.getRevenueSummary(1L, 30)

        assertFalse(response.platformRevenueAvailable)
        assertNotNull(response.platformRevenueUnavailableReason)
    }

    @org.junit.jupiter.api.Test
    fun `수익 권한이 없으면 재연동 CTA를 함께 반환한다`() {
        every { userRepository.findById(1L) } returns User(
            id = 1L,
            email = "creator@example.com",
            name = "Creator",
            provider = AuthProvider.GOOGLE,
            providerId = "google-1",
        )
        every { revenueRepository.getRevenueStatusCounts(1L, any(), any()) } returns listOf(
            PlatformRevenueStatusCount("YOUTUBE", RevenueStatus.PERMISSION_REQUIRED.name, 3L),
        )

        val response = useCase.getRevenueSummary(1L, 30)

        assertFalse(response.platformRevenueAvailable)
        assertTrue(response.platformRevenueReconnectRequired)
    }

    // ── 성장률: 비교할 이전 기간이 없으면 만들어내지 않는다 ────────────────────
    //
    // 예전에는 `previousTotal == 0 && currentTotal > 0` 이면 `100.0` 을 돌려줬다. 그래서 첫
    // 수익 1,000 원과 100만 원이 화면에서 똑같이 "+100%" 로 보였다. `0 → 0` 은 `0.0` 이라
    // "변화 없음"으로 보였는데, 비교할 기간에 데이터가 없다는 사실과 구분되지 않았다.
    // 대시보드 증감률과 같은 정책(MetricChange)을 쓴다.

    private fun givenUser() {
        every { userRepository.findById(1L) } returns User(
            id = 1L,
            email = "creator@example.com",
            name = "Creator",
            provider = AuthProvider.GOOGLE,
            providerId = "google-1",
        )
    }

    /** `getTotalRevenue(userId, from, to)` 는 현재/이전 기간 순으로 두 번 불린다. */
    private fun givenTotals(current: Long, previous: Long) {
        every { revenueRepository.getTotalRevenue(1L, any(), any()) } returnsMany listOf(current, previous)
    }

    @org.junit.jupiter.api.Test
    fun `이전 기간 수익이 없고 이번에 수익이 나면 성장률을 만들어내지 않는다`() {
        givenUser()
        givenTotals(current = 5_000_000L, previous = 0L)

        val response = useCase.getRevenueSummary(1L, 30)

        // 100.0 이 되살아나면 첫 수익이 정확한 성장률처럼 보인다.
        assertNull(response.growthPercent, "이전 기간이 0인데 성장률을 계산했다")
    }

    @org.junit.jupiter.api.Test
    fun `두 기간 모두 수익이 없으면 변화 없음이 아니라 비교 불가다`() {
        givenUser()
        givenTotals(current = 0L, previous = 0L)

        val response = useCase.getRevenueSummary(1L, 30)

        assertNull(response.growthPercent, "0.0 은 '변화 없음'으로 읽혀 비교 불가와 구분되지 않는다")
    }

    @org.junit.jupiter.api.Test
    fun `이전 기간 수익이 있으면 실제 성장률을 계산한다`() {
        givenUser()
        givenTotals(current = 150_000_000L, previous = 100_000_000L)

        val response = useCase.getRevenueSummary(1L, 30)

        assertEquals(50.0, response.growthPercent)
    }

    /** 현재가 0 이어도 이전 기간이 있으면 -100% 는 **측정된 사실**이다. */
    @org.junit.jupiter.api.Test
    fun `수익이 0으로 떨어지면 마이너스 성장률로 계산한다`() {
        givenUser()
        givenTotals(current = 0L, previous = 100_000_000L)

        val response = useCase.getRevenueSummary(1L, 30)

        assertEquals(-100.0, response.growthPercent)
    }

    // ── 플랫폼 비중: 분모가 0 이면 비율이 성립하지 않는다 ──────────────────────
    //
    // 예전에는 `if (total > 0) ... else 0.0` 이었다. 수익이 아직 한 푼도 잡히지 않았는데
    // 플랫폼 행은 있는 상태에서 **"비중 0%"** 라는 관측이 만들어졌고, 그 값이 유료 AI
    // 프롬프트(`RevenueInsightUseCase`)에도 그대로 들어갔다.

    private fun givenPlatforms(vararg platforms: PlatformRevenue) {
        every { revenueRepository.getPlatformRevenue(1L, any(), any()) } returns platforms.toList()
    }

    /** **이 케이스가 "비중 0%" 를 지어내던 자리다.** */
    @org.junit.jupiter.api.Test
    fun `전체 수익이 0이면 플랫폼 비중을 만들지 않는다`() {
        givenUser()
        givenTotals(current = 0L, previous = 0L)
        givenPlatforms(PlatformRevenue(platform = "YOUTUBE", totalRevenueMicro = 0L))

        val item = useCase.getRevenueSummary(1L, 30).platformBreakdown.single()

        assertNull(item.percentage, "분모가 0인데 비중을 계산했다")
        // 나머지 필드는 그대로다 — 계약을 바꾸지 않았다.
        assertEquals("YOUTUBE", item.platform)
        assertEquals(0L, item.revenueKrw)
    }

    /** **분모가 양수이면 그 플랫폼 수익 0 은 실측 비중 0% 다.** 감추면 관찰을 잃는다. */
    @org.junit.jupiter.api.Test
    fun `전체 수익이 있으면 수익 0인 플랫폼은 0퍼센트로 유지한다`() {
        givenUser()
        givenTotals(current = 100_000_000L, previous = 0L)
        givenPlatforms(
            PlatformRevenue(platform = "YOUTUBE", totalRevenueMicro = 100_000_000L),
            PlatformRevenue(platform = "TIKTOK", totalRevenueMicro = 0L),
        )

        val byPlatform = useCase.getRevenueSummary(1L, 30).platformBreakdown.associateBy { it.platform }

        assertEquals(100.0, byPlatform.getValue("YOUTUBE").percentage)
        assertEquals(0.0, byPlatform.getValue("TIKTOK").percentage, "실측 0% 를 미측정으로 감췄다")
    }

    /** `getPlatformRevenue` 도 같은 판정이어야 한다 — 갈라지면 화면마다 다른 답이 나온다. */
    @org.junit.jupiter.api.Test
    fun `플랫폼 수익 API도 분모가 0이면 비중을 만들지 않는다`() {
        givenUser()
        every { revenueRepository.getTotalRevenue(1L, any(), any()) } returns 0L
        givenPlatforms(PlatformRevenue(platform = "YOUTUBE", totalRevenueMicro = 0L))

        assertNull(useCase.getPlatformRevenue(1L, 30).platforms.single().percentage)
    }

    @org.junit.jupiter.api.Test
    fun `플랫폼 수익 API는 분모가 있으면 실측 비중을 낸다`() {
        givenUser()
        every { revenueRepository.getTotalRevenue(1L, any(), any()) } returns 200_000_000L
        givenPlatforms(
            PlatformRevenue(platform = "YOUTUBE", totalRevenueMicro = 50_000_000L),
            PlatformRevenue(platform = "TIKTOK", totalRevenueMicro = 0L),
        )

        val byPlatform = useCase.getPlatformRevenue(1L, 30).platforms.associateBy { it.platform }

        assertEquals(25.0, byPlatform.getValue("YOUTUBE").percentage)
        assertEquals(0.0, byPlatform.getValue("TIKTOK").percentage)
    }
}
