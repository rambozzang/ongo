package com.ongo.application.automation

import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.automation.AutomationRule
import com.ongo.domain.automation.AutomationRuleRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 성과 트리거가 **실제로 잰 숫자로만 발사되는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * `findDailyAnalyticsByChannelIds` 가 주는 `AnalyticsDaily` 에는 플랫폼이 없다. 그대로
 * 더하면
 *
 * - `TumblrClient.kt:141` 의 `total_notes`(좋아요+리블로그+답글 총합) → **조회수**
 * - `PinterestClient.kt:158/160` 의 `SAVE`(저장)·`PIN_CLICK`(클릭) → **좋아요·공유**
 *
 * 로 섞였다. 이 값들은 화면에 그려지고 끝나는 게 아니라 **알림을 발사한다.** 도달한 적
 * 없는 조회수 마일스톤을 축하하고, 재지도 않은 참여율 하락을 경고했다.
 *
 * 그리고 참여율 분모가 0 일 때 `0.0` 을 돌려주던 자리가 있었다. 최근 조회수가 0 이면
 * `recentRate = 0.0` → `dropRatio = 100%` 가 되어 **참여율을 한 번도 잰 적 없는 채널에
 * "급락" 알림**이 나갔다.
 *
 * ## 여기서 고정하는 것
 *
 * 1. 기존 세 규칙(마일스톤·바이럴·참여율 하락)의 **원래 동작**
 * 2. Tumblr·Pinterest 처럼 그 지표를 주지 않는 플랫폼의 행이 판정에 섞이지 않는 것
 * 3. 측정된 0 은 그대로 관측으로 남는 것
 */
class PerformanceTriggerEvaluatorTest {

    private val automationRuleRepository = mockk<AutomationRuleRepository>()
    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val evaluator = PerformanceTriggerEvaluator(
        automationRuleRepository = automationRuleRepository,
        analyticsRepository = analyticsRepository,
        videoUploadRepository = videoUploadRepository,
        eventPublisher = eventPublisher,
    )

    private val userId = 7L

    /** 업로드 1 = 판정 대상 플랫폼, 업로드 2 = 항상 YouTube(대조군). */
    private fun row(
        uploadId: Long,
        daysAgo: Long,
        views: Int = 0,
        likes: Int = 0,
        comments: Int = 0,
        shares: Int = 0,
    ) = AnalyticsDaily(
        videoUploadId = uploadId,
        date = LocalDate.now().minusDays(daysAgo),
        views = views,
        likes = likes,
        commentsCount = comments,
        shares = shares,
    )

    private fun rule(triggerType: String, config: Map<String, Any?> = emptyMap()) = AutomationRule(
        id = 1L,
        userId = userId,
        name = "규칙",
        triggerType = triggerType,
        actionType = "NOTIFY",
        isActive = true,
    ).copy(triggerConfig = config)

    /**
     * 규칙 하나와 행 목록으로 스케줄러를 한 번 돌리고, 이벤트가 나갔는지 돌려준다.
     *
     * @param platforms 업로드 id → 플랫폼. 행의 플랫폼을 이걸로 결정한다.
     */
    private fun fired(
        rule: AutomationRule,
        rows: List<AnalyticsDaily>,
        platforms: Map<Long, Platform> = mapOf(1L to Platform.YOUTUBE),
    ): Boolean {
        every { automationRuleRepository.findAll() } returns listOf(rule)
        every { analyticsRepository.findDailyAnalyticsByChannelIds(userId, null) } returns rows
        every { videoUploadRepository.findByUserId(userId) } returns platforms.map { (id, platform) ->
            VideoUpload(id = id, videoId = id, platform = platform, channelId = 1L)
        }

        val event = slot<PerformanceTriggerFiredEvent>()
        var published = false
        every { eventPublisher.publishEvent(capture(event)) } answers { published = true }

        evaluator.evaluatePerformanceTriggers()
        return published
    }

    // ══ 1) 기존 규칙 동작 회귀 ══════════════════════════════════════════════

    @Test
    @DisplayName("조회수 합이 마일스톤을 넘으면 발사한다")
    fun milestoneFiresOnMeasuredViews() {
        val rows = listOf(row(1L, daysAgo = 1, views = 600), row(1L, daysAgo = 2, views = 500))

        assertTrue(fired(rule("VIEWS_MILESTONE"), rows), "1,100 회는 기본 1,000 마일스톤을 넘는다")
    }

    @Test
    @DisplayName("마일스톤에 못 미치면 발사하지 않는다")
    fun milestoneStaysQuietBelowThreshold() {
        assertFalse(fired(rule("VIEWS_MILESTONE"), listOf(row(1L, daysAgo = 1, views = 999))))
    }

    @Test
    @DisplayName("설정한 마일스톤 값을 그대로 쓴다")
    fun milestoneUsesConfiguredValues() {
        val config = mapOf<String, Any?>("milestones" to listOf(500))

        assertTrue(fired(rule("VIEWS_MILESTONE", config), listOf(row(1L, daysAgo = 1, views = 500))))
    }

    @Test
    @DisplayName("최근 조회수가 평균의 배수를 넘으면 바이럴로 판정한다")
    fun viralFiresOnSpike() {
        /*
         * 평균은 **스파이크 자신까지 포함**해 계산된다. 행이 적으면 스파이크가 평균을
         * 끌어올려 자기 기준을 넘지 못한다(행 3 개면 수학적으로 절대 발사되지 않는다).
         * 여기서는 평소 3 일 + 스파이크 1 일 → 평균 1,325, 기준 3,975.
         */
        val rows = listOf(
            row(1L, daysAgo = 20, views = 100),
            row(1L, daysAgo = 19, views = 100),
            row(1L, daysAgo = 18, views = 100),
            row(1L, daysAgo = 1, views = 5_000),
        )

        assertTrue(fired(rule("VIRAL_DETECTED"), rows), "평균 대비 3배를 크게 넘었다")
    }

    @Test
    @DisplayName("평소 수준이면 바이럴로 판정하지 않는다")
    fun viralStaysQuietWithoutSpike() {
        val rows = listOf(row(1L, daysAgo = 20, views = 100), row(1L, daysAgo = 1, views = 120))

        assertFalse(fired(rule("VIRAL_DETECTED"), rows))
    }

    @Test
    @DisplayName("참여율이 기준 이상 떨어지면 발사한다")
    fun engagementDropFires() {
        val rows = listOf(
            // 과거: 1,000 회에 참여 200 → 20%
            row(1L, daysAgo = 30, views = 1_000, likes = 200),
            // 최근: 1,000 회에 참여 10 → 1% (95% 하락)
            row(1L, daysAgo = 1, views = 1_000, likes = 10),
        )

        assertTrue(fired(rule("ENGAGEMENT_DROP"), rows))
    }

    @Test
    @DisplayName("참여율이 유지되면 발사하지 않는다")
    fun engagementDropStaysQuietWhenStable() {
        val rows = listOf(
            row(1L, daysAgo = 30, views = 1_000, likes = 200),
            row(1L, daysAgo = 1, views = 1_000, likes = 200),
        )

        assertFalse(fired(rule("ENGAGEMENT_DROP"), rows))
    }

    @Test
    @DisplayName("최근 1시간 내 발사한 규칙은 다시 발사하지 않는다")
    fun recentlyTriggeredRuleStaysQuiet() {
        val recentlyFired = rule("VIEWS_MILESTONE").copy(lastTriggeredAt = LocalDateTime.now().minusMinutes(5))

        assertFalse(fired(recentlyFired, listOf(row(1L, daysAgo = 1, views = 100_000))))
    }

    @Test
    @DisplayName("비활성 규칙은 평가하지 않는다")
    fun inactiveRuleIsNotEvaluated() {
        val inactive = rule("VIEWS_MILESTONE").copy(isActive = false)

        assertFalse(fired(inactive, listOf(row(1L, daysAgo = 1, views = 100_000))))
    }

    // ══ 2) 지원하지 않는 플랫폼의 행 ════════════════════════════════════════

    /**
     * **이 케이스가 도달한 적 없는 마일스톤을 축하하던 자리다.**
     *
     * Tumblr 의 `total_notes` 90 만이 조회수로 들어가면 어떤 마일스톤이든 즉시 넘는다.
     */
    @Test
    @DisplayName("Tumblr 노트 총합은 조회수 마일스톤을 만들지 않는다")
    fun tumblrNotesDoNotFireViewsMilestone() {
        val rows = listOf(row(1L, daysAgo = 1, views = 900_000))

        assertFalse(
            fired(rule("VIEWS_MILESTONE"), rows, mapOf(1L to Platform.TUMBLR)),
            "노트 총합을 조회수로 읽어 마일스톤을 발사했다",
        )
    }

    @Test
    @DisplayName("Tumblr 노트 총합은 바이럴 판정에도 섞이지 않는다")
    fun tumblrNotesDoNotFireViral() {
        val rows = listOf(
            row(1L, daysAgo = 20, views = 100),
            row(1L, daysAgo = 1, views = 900_000),
        )

        assertFalse(fired(rule("VIRAL_DETECTED"), rows, mapOf(1L to Platform.TUMBLR)))
    }

    /** YouTube 와 Tumblr 를 같이 쓰면 **YouTube 행만** 합산돼야 한다. */
    @Test
    @DisplayName("혼합 채널에서는 지원하는 플랫폼 행만 합산한다")
    fun mixedPlatformsSumOnlySupportedRows() {
        val rows = listOf(
            row(1L, daysAgo = 1, views = 400),   // YouTube — 실제 조회수
            row(2L, daysAgo = 1, views = 900_000), // Tumblr — 노트 총합
        )
        val platforms = mapOf(1L to Platform.YOUTUBE, 2L to Platform.TUMBLR)

        // 400 회는 기본 최소 마일스톤 1,000 에 못 미친다. 섞였다면 즉시 발사됐을 것이다.
        assertFalse(fired(rule("VIEWS_MILESTONE"), rows, platforms), "Tumblr 행이 조회수에 섞였다")
        // 같은 데이터에서 마일스톤을 400 으로 낮추면 YouTube 행만으로 발사된다.
        val lowered = rule("VIEWS_MILESTONE", mapOf("milestones" to listOf(400)))
        assertTrue(fired(lowered, rows, platforms), "YouTube 실측까지 버렸다")
    }

    /**
     * Pinterest 는 `SAVE` 를 좋아요로, `PIN_CLICK` 을 공유로 매핑한다. 참여 수가 아니다.
     */
    @Test
    @DisplayName("Pinterest 저장·클릭은 참여율 하락 판정에 쓰이지 않는다")
    fun pinterestSavesDoNotDriveEngagementDrop() {
        val rows = listOf(
            row(1L, daysAgo = 30, views = 1_000, likes = 500, shares = 500),
            row(1L, daysAgo = 1, views = 1_000, likes = 0, shares = 0),
        )

        assertFalse(
            fired(rule("ENGAGEMENT_DROP"), rows, mapOf(1L to Platform.PINTEREST)),
            "저장·클릭을 참여로 읽어 하락을 경고했다",
        )
    }

    /** 조회수를 물어볼 수 있는 행이 아예 없으면 어떤 규칙도 판정할 수 없다. */
    @Test
    @DisplayName("지원 플랫폼 행이 없으면 어떤 규칙도 발사하지 않는다")
    fun noSupportedRowsFireNothing() {
        val rows = listOf(row(1L, daysAgo = 1, views = 900_000, likes = 900_000))
        val tumblrOnly = mapOf(1L to Platform.TUMBLR)

        listOf("VIEWS_MILESTONE", "VIRAL_DETECTED", "ENGAGEMENT_DROP").forEach { type ->
            assertFalse(fired(rule(type), rows, tumblrOnly), "$type 이 미측정 데이터로 발사됐다")
        }
    }

    // ══ 3) 측정된 0 과 미측정의 구분 ════════════════════════════════════════

    /**
     * **이 케이스가 없는 "참여율 급락" 을 경고하던 자리다.**
     *
     * 최근 조회수가 0 이면 참여율의 분모가 없다. 예전에는 `0.0` 을 돌려줘 하락률이
     * 100% 로 계산됐다.
     */
    @Test
    @DisplayName("최근 조회수가 0이면 참여율 하락을 만들지 않는다")
    fun zeroRecentViewsDoNotCreateADrop() {
        val rows = listOf(
            row(1L, daysAgo = 30, views = 1_000, likes = 200),
            row(1L, daysAgo = 1, views = 0, likes = 0),
        )

        assertFalse(fired(rule("ENGAGEMENT_DROP"), rows), "분모 없는 0 을 100% 하락으로 읽었다")
    }

    /** 조회수가 실제로 0 으로 측정됐다면 마일스톤은 그냥 못 넘은 것이다(오류가 아니다). */
    @Test
    @DisplayName("측정된 0 조회수는 관측으로 남고 마일스톤을 넘지 않는다")
    fun measuredZeroViewsAreAnObservation() {
        assertFalse(fired(rule("VIEWS_MILESTONE"), listOf(row(1L, daysAgo = 1, views = 0))))
    }

    /** 참여가 실제로 0 이었고 조회수가 있으면 그 하락은 진짜다. */
    @Test
    @DisplayName("조회수가 있는데 참여가 0이면 실제 하락으로 본다")
    fun measuredZeroEngagementWithViewsIsARealDrop() {
        val rows = listOf(
            row(1L, daysAgo = 30, views = 1_000, likes = 200),
            row(1L, daysAgo = 1, views = 1_000, likes = 0),
        )

        assertTrue(fired(rule("ENGAGEMENT_DROP"), rows), "실측된 참여 0 을 미측정으로 버렸다")
    }

    @Test
    @DisplayName("발사한 이벤트에 규칙 정보를 담는다")
    fun firedEventCarriesRuleContext() {
        every { automationRuleRepository.findAll() } returns listOf(rule("VIEWS_MILESTONE"))
        every { analyticsRepository.findDailyAnalyticsByChannelIds(userId, null) } returns
            listOf(row(1L, daysAgo = 1, views = 2_000))
        every { videoUploadRepository.findByUserId(userId) } returns
            listOf(VideoUpload(id = 1L, videoId = 1L, platform = Platform.YOUTUBE, channelId = 1L))

        val event = slot<PerformanceTriggerFiredEvent>()
        every { eventPublisher.publishEvent(capture(event)) } answers {}

        evaluator.evaluatePerformanceTriggers()

        assertEquals(userId, event.captured.userId)
        assertEquals(1L, event.captured.ruleId)
        assertEquals("VIEWS_MILESTONE", event.captured.triggerType)
    }
}
