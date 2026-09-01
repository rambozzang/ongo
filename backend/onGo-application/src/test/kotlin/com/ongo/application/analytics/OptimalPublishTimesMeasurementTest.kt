package com.ongo.application.analytics

import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.CrossPlatformDetailRaw
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 최적 게시 시간 추천이 **실제 게시 시각에서만** 나오는지 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * val hour = record.createdAt?.hour ?: 12
 * ```
 *
 * `record` 는 `analytics_daily` 행이고 그 `createdAt` 은 **우리 DB 에 행이 만들어진 시각**,
 * 즉 `AnalyticsSyncScheduler` 가 돈 시각이다. 스케줄러는 고정 주기로 돌므로 이 값은
 * 동기화 시각에 몰린다 — 실제 게시 시각과 아무 관계가 없다. `null` 이면 **정오로 가정**해
 * 그 슬롯에 쌓기까지 했다.
 *
 * 그렇게 만든 요일 × 시각 슬롯을 화면은 "예상 조회수 · 참여율 · 신뢰도" 로 보여줬다.
 */
class OptimalPublishTimesMeasurementTest {

    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val useCase = AnalyticsUseCase(
        analyticsRepository = analyticsRepository,
        userRepository = mockk(relaxed = true),
        videoRepository = mockk(relaxed = true),
        videoUploadRepository = mockk(relaxed = true),
        creditRepository = mockk(relaxed = true),
    )

    private val userId = 7L

    private fun upload(
        uploadId: Long,
        platform: String = "YOUTUBE",
        publishedAt: LocalDateTime?,
        views: Long = 1000,
        likes: Long = 0,
        comments: Long = 0,
        shares: Long = 0,
    ) = CrossPlatformDetailRaw(
        videoId = uploadId, videoTitle = "v$uploadId", thumbnailUrls = emptyList(),
        publishedAt = publishedAt,
        platform = platform, videoUploadId = uploadId,
        views = views, likes = likes, comments = comments, shares = shares,
        watchTimeSeconds = 0, revenueMicro = 0, impressions = 0, avgViewDurationSeconds = 0,
    )

    private fun given(vararg uploads: CrossPlatformDetailRaw) {
        every { analyticsRepository.findCrossPlatformDetailMetrics(userId, any()) } returns uploads.toList()
    }

    /** 2026-08-10 은 월요일. `dayOfWeek.value % 7` 기준으로 1 이다. */
    private val mondayAt9 = LocalDateTime.of(2026, 8, 10, 9, 0)

    // ── 실제 게시 시각만 쓴다 ────────────────────────────────────────────────

    /** **이 케이스가 동기화 시각을 추천으로 만들던 자리다.** */
    @Test
    @DisplayName("게시 시각의 요일과 시각을 그대로 슬롯으로 쓴다")
    fun slotsComeFromPublishedAt() {
        given(upload(1, publishedAt = mondayAt9))

        val slot = useCase.getOptimalPublishTimes(userId, null).slots.single()

        assertEquals(1, slot.dayOfWeek, "월요일이 아니다")
        assertEquals("월요일", slot.dayLabel)
        assertEquals(9, slot.hour, "게시 시각의 시가 아니다")
        assertEquals("09:00", slot.timeLabel)
    }

    /**
     * **게시 시각을 모르면 슬롯을 만들지 않는다.** 예전에는 정오로 가정해 12시 슬롯에
     * 쌓았고, 그것이 "12:00 추천" 으로 화면에 나왔다.
     */
    @Test
    @DisplayName("게시 시각이 없으면 정오로 가정하지 않고 제외한다")
    fun missingPublishedAtIsExcludedNotAssumedNoon() {
        given(upload(1, publishedAt = null))

        val response = useCase.getOptimalPublishTimes(userId, null)

        assertTrue(response.slots.isEmpty(), "정오 슬롯을 지어냈다")
        assertTrue(response.slots.none { it.hour == 12 })
        assertTrue(response.unavailableReason!!.isNotBlank())
    }

    @Test
    @DisplayName("게시 시각을 아는 표본만 섞여 있어도 그것만 쓴다")
    fun onlyUploadsWithPublishedAtAreUsed() {
        given(
            upload(1, publishedAt = mondayAt9),
            upload(2, publishedAt = null),
        )

        val slots = useCase.getOptimalPublishTimes(userId, null).slots

        assertEquals(1, slots.size)
        assertEquals(9, slots.single().hour)
    }

    /** 조회수가 0 이면 참여율의 분모가 없고 비교할 성과도 없다. */
    @Test
    @DisplayName("조회수가 0인 행은 표본에서 제외한다")
    fun zeroViewUploadsAreExcluded() {
        given(upload(1, publishedAt = mondayAt9, views = 0))

        val response = useCase.getOptimalPublishTimes(userId, null)

        assertTrue(response.slots.isEmpty())
        assertTrue(response.unavailableReason!!.isNotBlank())
    }

    // ── 근거 없는 결과를 만들지 않는다 ───────────────────────────────────────

    @Test
    @DisplayName("표본이 하나도 없으면 빈 결과와 사유를 내려보낸다")
    fun noEligibleDataReturnsEmptyWithReason() {
        given()

        val response = useCase.getOptimalPublishTimes(userId, null)

        assertTrue(response.slots.isEmpty())
        assertEquals(AnalyticsUseCase.OPTIMAL_TIMES_UNAVAILABLE, response.unavailableReason)
    }

    /** 사유는 숫자가 아니라 문장이어야 한다. 슬롯을 지어내면 그 시각이 추천이 된다. */
    @Test
    @DisplayName("미측정 사유에 숫자가 들어가지 않는다")
    fun unavailableReasonIsNotANumber() {
        assertTrue(
            !Regex("[0-9]").containsMatchIn(AnalyticsUseCase.OPTIMAL_TIMES_UNAVAILABLE),
            "사유에 숫자가 있다: ${AnalyticsUseCase.OPTIMAL_TIMES_UNAVAILABLE}",
        )
    }

    // ── 참여율은 지원 지표만 ─────────────────────────────────────────────────

    /**
     * Facebook 은 공유를 주지 않는다. 저장된 0 을 분자에 더하면 그 슬롯의 참여율이
     * 실제보다 낮게 나와 추천 순위가 뒤바뀐다.
     */
    @Test
    @DisplayName("플랫폼이 주지 않는 지표는 참여율 분자에서 제외한다")
    fun unsupportedEngagementMetricsAreExcluded() {
        given(
            upload(1, platform = "FACEBOOK", publishedAt = mondayAt9, views = 1000, likes = 80, comments = 20, shares = 0),
        )

        val slot = useCase.getOptimalPublishTimes(userId, null).slots.single()

        // (80 + 20) / 1000 = 10.0%. 미수집 공유 0 이 섞이면 값이 달라진다.
        assertEquals(10.0, slot.engagementRate)
    }

    /** 지원 지표의 측정된 0 은 그대로 더한다. */
    @Test
    @DisplayName("지원 지표의 측정된 0은 그대로 반영한다")
    fun supportedZerosAreCounted() {
        given(
            upload(1, platform = "YOUTUBE", publishedAt = mondayAt9, views = 1000, likes = 50, comments = 0, shares = 0),
        )

        val slot = useCase.getOptimalPublishTimes(userId, null).slots.single()

        assertEquals(5.0, slot.engagementRate)
    }

    // ── 신뢰도와 플랫폼 필터 ─────────────────────────────────────────────────

    /** 신뢰도는 실제 표본 수로만 정한다. 임의 숫자를 넣지 않는다. */
    @Test
    @DisplayName("신뢰도는 표본 수에 비례한다")
    fun confidenceScalesWithSampleCount() {
        val single = run {
            given(upload(1, publishedAt = mondayAt9))
            useCase.getOptimalPublishTimes(userId, null).slots.single().confidenceScore
        }
        val triple = run {
            given(
                upload(1, publishedAt = mondayAt9),
                upload(2, publishedAt = mondayAt9),
                upload(3, publishedAt = mondayAt9),
            )
            useCase.getOptimalPublishTimes(userId, null).slots.single().confidenceScore
        }

        assertTrue(triple > single, "표본이 늘었는데 신뢰도가 오르지 않았다")
        assertEquals(100.0 / AnalyticsUseCase.MAX_CONFIDENCE_SAMPLES, single, 0.01)
    }

    /** 플랫폼 필터는 기존대로 동작해야 한다. */
    @Test
    @DisplayName("플랫폼 필터는 그대로 유지된다")
    fun platformFilterStillApplies() {
        given(
            upload(1, platform = "YOUTUBE", publishedAt = mondayAt9),
            upload(2, platform = "TIKTOK", publishedAt = LocalDateTime.of(2026, 8, 11, 20, 0)),
        )

        val slots = useCase.getOptimalPublishTimes(userId, Platform.TIKTOK).slots

        assertEquals(1, slots.size)
        assertEquals(20, slots.single().hour, "TikTok 게시 시각이 아니다")
    }

    @Test
    @DisplayName("필터에 맞는 표본이 없으면 빈 결과와 사유를 내려보낸다")
    fun platformFilterWithNoMatchReturnsEmpty() {
        given(upload(1, platform = "YOUTUBE", publishedAt = mondayAt9))

        val response = useCase.getOptimalPublishTimes(userId, Platform.TIKTOK)

        assertTrue(response.slots.isEmpty())
        assertEquals(AnalyticsUseCase.OPTIMAL_TIMES_UNAVAILABLE, response.unavailableReason)
    }

    @Test
    @DisplayName("측정된 표본이 있으면 사유를 남기지 않는다")
    fun measuredSampleHasNoReason() {
        given(upload(1, publishedAt = mondayAt9))

        assertNull(useCase.getOptimalPublishTimes(userId, null).unavailableReason)
    }

    // ── 분석 API 가 없는 플랫폼 ──────────────────────────────────────────────

    /**
     * **`forPlatform` 이 알 수 없는 플랫폼에 `emptySet()` 을 돌려주던 자리다.**
     *
     * 호출부는 `metric !in unavailable` 로 읽으므로 빈 집합은 "모든 지표를 수집한다" 가
     * 된다. `Platform` enum 은 13개인데 계약 맵은 12개였고 빠진 하나가 Naver Clip 이라,
     * 그 플랫폼의 하드코딩 0 이 참여율 표본으로 들어갔다.
     *
     * 근거: `NaverClipClient.getVideoAnalytics` 는 값을 돌려주지 않고 예외를 던진다.
     */
    @Test
    @DisplayName("분석 API 가 없는 플랫폼의 행은 추천 표본에서 제외한다")
    fun platformsWithoutAnalyticsApiAreExcluded() {
        given(upload(1, platform = "NAVER_CLIP", publishedAt = mondayAt9, views = 5_000))

        val response = useCase.getOptimalPublishTimes(userId, null)

        assertTrue(response.slots.isEmpty(), "수집하지 않는 플랫폼 행으로 추천을 만들었다")
        assertEquals(AnalyticsUseCase.OPTIMAL_TIMES_UNAVAILABLE, response.unavailableReason)
    }

    /** 알 수 없는 플랫폼 문자열도 같은 정책이다 — fail-closed. */
    @Test
    @DisplayName("알 수 없는 플랫폼의 행은 추천 표본에서 제외한다")
    fun unknownPlatformRowsAreExcluded() {
        given(upload(1, platform = "SOME_NEW_PLATFORM", publishedAt = mondayAt9, views = 5_000))

        assertTrue(useCase.getOptimalPublishTimes(userId, null).slots.isEmpty())
    }

    /** 지원 플랫폼 행이 섞여 있으면 그것만 표본이 된다. */
    @Test
    @DisplayName("지원 플랫폼 행만 골라 추천을 만든다")
    fun onlySupportedPlatformRowsBecomeSamples() {
        given(
            upload(1, platform = "YOUTUBE", publishedAt = mondayAt9, views = 1_000, likes = 100),
            upload(2, platform = "NAVER_CLIP", publishedAt = LocalDateTime.of(2026, 8, 11, 20, 0), views = 9_000),
        )

        val slots = useCase.getOptimalPublishTimes(userId, null).slots

        assertEquals(1, slots.size, "수집하지 않는 플랫폼으로 슬롯을 만들었다")
        assertEquals(9, slots.single().hour)
    }

    // ── 참여율 표본이 하나도 없을 때 ────────────────────────────────────────
    //
    // 위 `unsupportedEngagementMetricsAreExcluded` 는 **일부** 지표만 빠지는 경우다.
    // Pinterest 처럼 `LIKES`·`COMMENTS`·`SHARES` 를 **전부** 주지 않는 플랫폼이면 그
    // 슬롯의 참여율 표본이 비는데, 예전에는 `medianDouble(emptyList()) = 0.0` 이 나와
    // 걸러낸 값이 슬롯 단위에서 **0 으로 되살아났다.**

    private val tuesdayAt20 = LocalDateTime.of(2026, 8, 11, 20, 0)

    /** 표본 1 건일 때의 신뢰도. 상수를 복사하면 임계값이 바뀔 때 테스트가 조용히 틀어진다. */
    private fun oneSampleConfidence(): Double =
        1.0 / AnalyticsUseCase.MAX_CONFIDENCE_SAMPLES * 100.0

    /** **이 케이스가 "참여율 0%" 를 관측처럼 내보내던 자리다.** */
    @Test
    @DisplayName("참여 지표를 하나도 주지 않는 플랫폼의 슬롯은 참여율이 null 이다")
    fun slotWithoutAnyEngagementSampleIsNull() {
        given(upload(1, platform = "PINTEREST", publishedAt = mondayAt9, views = 1_000, likes = 500))

        val slot = useCase.getOptimalPublishTimes(userId, null).slots.single()

        assertNull(slot.engagementRate, "재지 않은 참여율을 0 으로 내보냈다")
        // 조회수는 Pinterest 도 보고한다 — 함께 죽으면 안 된다.
        assertEquals(1_000L, slot.expectedViews)
        assertTrue(slot.confidenceScore > 0.0)
    }

    /** **측정된 0 은 관측이다.** 과도한 null 처리 회귀를 막는다. */
    @Test
    @DisplayName("보고하는 플랫폼의 측정된 참여율 0 은 0 으로 남는다")
    fun measuredZeroEngagementStaysZero() {
        given(upload(1, platform = "YOUTUBE", publishedAt = mondayAt9, views = 1_000, likes = 0, comments = 0, shares = 0))

        val slot = useCase.getOptimalPublishTimes(userId, null).slots.single()

        assertEquals(0.0, slot.engagementRate, "실측 0% 를 미측정으로 감췄다")
    }

    // ── 점수가 미수집을 벌점으로 쓰지 않는다 ────────────────────────────────

    /**
     * **점수는 오직 순위에만 쓰인다.** 참여 항을 잰 슬롯과 못 잰 슬롯이 섞이면,
     * 못 잰 쪽에 `0` 을 넣는 것은 **측정하지 못했다는 이유로 주는 벌점**이다.
     * 그래서 한 슬롯이라도 못 재면 참여 항을 전부 뺀다 — 같은 구성요소로 비교한다.
     *
     * 여기서 Pinterest 슬롯(조회수 5,000)은 YouTube 슬롯(조회수 1,000)보다 조회수가
     * 5 배다. 참여 항이 0 으로 들어가던 예전에는 YouTube 의 참여율 10% 가
     * `10 * 100 * 0.3 = 300` 점을 더해 순위를 뒤집었다.
     */
    @Test
    @DisplayName("참여율을 못 잰 슬롯이 있으면 참여 항을 모든 슬롯에서 뺀다")
    fun engagementTermIsDroppedForEveryoneWhenAnySlotLacksIt() {
        given(
            upload(1, platform = "YOUTUBE", publishedAt = mondayAt9, views = 1_000, likes = 100),
            upload(2, platform = "PINTEREST", publishedAt = tuesdayAt20, views = 5_000, likes = 900),
        )

        val slots = useCase.getOptimalPublishTimes(userId, null).slots

        // 조회수가 5 배인 Pinterest 슬롯이 1 위여야 한다.
        assertEquals(20, slots.first().hour, "미수집 슬롯이 벌점을 받아 순위가 밀렸다")
        assertNull(slots.first().engagementRate)
        // 두 슬롯 모두 조회수·신뢰도만으로 계산된다.
        val youtube = slots.single { it.hour == 9 }
        val expected = 1_000 * 0.6 + oneSampleConfidence() * 0.1
        assertEquals(Math.round(expected * 100) / 100.0, youtube.score, "참여 항이 점수에 남아 있다")
    }

    /**
     * 모든 슬롯이 참여율을 쟀으면 예전 3항 점수를 그대로 쓴다.
     * 측정된 참여율이 순위에 반영되던 기존 동작을 잃지 않는다.
     */
    @Test
    @DisplayName("모든 슬롯이 참여율을 재면 참여 항을 점수에 반영한다")
    fun engagementTermStaysWhenEverySlotMeasuredIt() {
        given(
            upload(1, platform = "YOUTUBE", publishedAt = mondayAt9, views = 1_000, likes = 100),
            upload(2, platform = "YOUTUBE", publishedAt = tuesdayAt20, views = 1_000, likes = 0),
        )

        val slots = useCase.getOptimalPublishTimes(userId, null).slots

        // 참여율 10% 인 월요일이 0% 인 화요일보다 앞선다.
        assertEquals(9, slots.first().hour, "측정된 참여율이 순위에 반영되지 않았다")
        val monday = slots.single { it.hour == 9 }
        val expected = 1_000 * 0.6 + 10.0 * 100 * 0.3 + oneSampleConfidence() * 0.1
        assertEquals(Math.round(expected * 100) / 100.0, monday.score, "참여 항이 점수에서 빠졌다")
    }

    /** 참여율이 전부 미수집이면 순위는 조회수·신뢰도로만 정해진다. */
    @Test
    @DisplayName("모두 미수집이면 조회수 순으로 추천한다")
    fun allUnmeasuredRanksByViews() {
        given(
            upload(1, platform = "PINTEREST", publishedAt = mondayAt9, views = 1_000),
            upload(2, platform = "PINTEREST", publishedAt = tuesdayAt20, views = 5_000),
        )

        val slots = useCase.getOptimalPublishTimes(userId, null).slots

        assertEquals(20, slots.first().hour, "조회수가 많은 시간대가 밀렸다")
        assertTrue(slots.all { it.engagementRate == null })
    }
}
