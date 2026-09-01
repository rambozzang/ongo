package com.ongo.application.analytics

import com.ongo.application.ai.PromptTemplates
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.DashboardKpi
import com.ongo.domain.analytics.MetricChange
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 대시보드 구독 지표가 **미수집과 실측 0 을 구분하는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * `subscriber_gained` 를 실제로 조회하는 어댑터는 `YouTubeClient` 하나뿐인데
 * (`YouTubeClient.kt:149`), 저장소는 전 플랫폼을 그냥 합산하고 `?: 0L` 로 채웠다.
 * 업로드가 아예 없을 때도 `totalSubscribers = 0`, `totalSubscribersChange = 0` 을 돌려줬다.
 *
 * 그래서 **세 가지 서로 다른 상황이 화면에서 전부 "신규 구독 0명"** 이었다.
 *
 * 1. YouTube 에 올렸고 그 기간에 실제로 0명이 늘었다 → **관측**
 * 2. TikTok 만 쓴다 → 물어볼 곳이 없다
 * 3. 아직 업로드가 없다 → 측정 대상 자체가 없다
 *
 * 1 만 숫자여야 한다.
 */
class DashboardSubscriberMeasurementTest {

    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val creditRepository = mockk<CreditRepository>(relaxed = true)

    private val useCase = AnalyticsUseCase(
        analyticsRepository = analyticsRepository,
        userRepository = userRepository,
        videoRepository = mockk<VideoRepository>(relaxed = true),
        videoUploadRepository = mockk<VideoUploadRepository>(relaxed = true),
        creditRepository = creditRepository,
    )

    private val userId = 7L

    private fun given(subscribers: Long?, change: Long?) {
        every { analyticsRepository.getDashboardKpi(userId, any()) } returns DashboardKpi(
            totalViews = 1_000,
            totalViewsChange = 12.5,
            totalSubscribers = subscribers,
            totalSubscribersChange = change,
            totalLikes = 100,
            totalLikesChange = 5.0,
            totalComments = 20,
            creditBalance = 0,
            creditTotal = 0,
        )
    }

    // ── 미수집은 null 로 전달된다 ────────────────────────────────────────────

    /** **이 케이스가 "신규 구독 0명" 을 성과로 그리던 자리다.** */
    @Test
    @DisplayName("구독을 수집하는 플랫폼이 없으면 응답도 null 이다")
    fun unmeasuredSubscribersStayNull() {
        given(subscribers = null, change = null)

        val response = useCase.getDashboardKpi(userId, 30)

        assertNull(response.totalSubscribers, "미수집을 0 으로 채웠다")
        assertNull(response.subscribersChange, "미수집인데 증감을 지어냈다")
        // 다른 지표는 살아 있어야 한다.
        assertEquals(1_000L, response.totalViews)
        assertEquals(100L, response.totalLikes)
    }

    // ── 측정된 0 은 보존 ─────────────────────────────────────────────────────

    /**
     * **YouTube 행이 있는 상태의 0 은 관측이다.** 그 기간에 실제로 아무도 구독하지
     * 않았다는 사실이므로, null 로 감추면 실제 관찰을 잃는다.
     */
    @Test
    @DisplayName("측정된 구독 증가 0은 숫자로 전달한다")
    fun measuredZeroSubscribersIsPreserved() {
        given(subscribers = 0, change = 0)

        val response = useCase.getDashboardKpi(userId, 30)

        assertEquals(0L, response.totalSubscribers, "실측 0 을 미측정으로 감췄다")
        assertEquals(0L, response.subscribersChange)
    }

    @Test
    @DisplayName("측정된 구독 증가는 그대로 전달한다")
    fun measuredSubscribersArePassedThrough() {
        given(subscribers = 42, change = 12)

        val response = useCase.getDashboardKpi(userId, 30)

        assertEquals(42L, response.totalSubscribers)
        assertEquals(12L, response.subscribersChange)
    }

    /** 현재는 측정됐지만 이전 기간이 미측정이면 증감은 성립하지 않는다. */
    @Test
    @DisplayName("한쪽 기간만 측정되면 증감을 만들지 않는다")
    fun oneSidedMeasurementProducesNoChange() {
        given(subscribers = 42, change = null)

        val response = useCase.getDashboardKpi(userId, 30)

        assertEquals(42L, response.totalSubscribers)
        assertNull(response.subscribersChange, "비교 기준이 없는데 증감을 지어냈다")
    }

    // ── AI 프롬프트에 "null" 이 들어가지 않는다 ──────────────────────────────

    /**
     * **`Long?.toString()` 은 문자열 `"null"` 을 만든다.**
     *
     * `StrategyCoachUseCase`·`WeeklyDigestUseCase`·`GenerateReportUseCase` 가 그 값을
     * `{subscriberChange}` 자리에 그대로 넣고 있었다. 유료 LLM 호출에 `"null"` 이 들어가면
     * 모델이 그것을 수치로 읽고 없는 사실을 지어낸다.
     */
    @Test
    @DisplayName("미측정 구독 증감은 프롬프트에 문장으로 들어간다")
    fun unmeasuredSubscriberChangeBecomesASentence() {
        val rendered = MetricChange.describeCount(null)

        assertEquals(MetricChange.NOT_MEASURED_TEXT, rendered)
        assertFalse(rendered.contains("null"), "프롬프트에 리터럴 null 이 들어간다")
        assertTrue(rendered.isNotBlank())
    }

    @Test
    @DisplayName("측정된 구독 증감은 숫자 그대로 프롬프트에 들어간다")
    fun measuredSubscriberChangeStaysANumber() {
        assertEquals("12", MetricChange.describeCount(12))
        // 측정된 0 은 숫자다 — 문장으로 바꾸면 실제 관찰을 잃는다.
        assertEquals("0", MetricChange.describeCount(0))
    }

    /** 미측정 문구와 비교 불가 문구는 서로 다른 상황이므로 구분돼야 한다. */
    @Test
    @DisplayName("미측정과 비교 불가는 다른 문구다")
    fun notMeasuredDiffersFromNotComparable() {
        assertTrue(MetricChange.NOT_MEASURED_TEXT != MetricChange.UNAVAILABLE_TEXT)
    }

    /** 프롬프트 템플릿에 자리표시자가 남아 있어야 위 치환이 의미를 갖는다. */
    @Test
    @DisplayName("프롬프트 템플릿에 구독 증감 자리표시자가 있다")
    fun promptTemplatesStillHaveThePlaceholder() {
        listOf(
            PromptTemplates.STRATEGY_COACH_USER,
            PromptTemplates.WEEKLY_DIGEST_USER,
            PromptTemplates.PERFORMANCE_REPORT_USER,
        ).forEach { template ->
            assertTrue("{subscriberChange}" in template, "자리표시자가 사라졌다")
        }
    }
}
