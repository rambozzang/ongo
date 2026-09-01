package com.ongo.application.analytics

import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 성과 점수의 하위 지표가 **임의 기준값으로 판정을 지어내지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 다섯 하위 점수는 전부 **비율**인데, 분모나 비교 기준이 없을 때 그 자리에 만들어 낸
 * 숫자를 넣었다.
 *
 * ```
 * val totalViews = videoAnalytics.sumOf { it.views }.coerceAtLeast(1)   // 조회 0 → 분모 1
 * val channelAvgViews = if (...) ... else 1.0                            // 채널 평균 1회
 * val channelAvgRate  = if (...) ... else 0.001                          // 전환율 0.1%
 * ratio = conversionRate / channelAvgRate.coerceAtLeast(0.0001)
 * ```
 *
 * 그 결과:
 *
 * - 조회수가 0 인 영상이 "참여율 0점" 을 받았다 — 분모가 없어 비율이 정의되지도 않는데
 *   "참여가 없었다" 는 관측이 됐다.
 * - 채널 평균 조회수가 `1.0` 이면 **조회수 2회짜리 영상이 조회속도 100점**을 받았다.
 * - 시청 시간은 YouTube 만 수집한다. 다른 플랫폼만 쓰는 크리에이터는 채널 평균이 0 →
 *   `coerceAtLeast(1.0)` → 시청시간 0점이 되어 **총점의 20% 를 항상 잃었다.**
 */
class PerformanceScoreMeasurementTest {

    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()

    private val useCase = PerformanceScoreUseCase(
        analyticsRepository, videoRepository, videoUploadRepository,
    )

    private val userId = 7L
    private val videoId = 42L
    private val uploadId = 101L

    private fun row(
        day: Int,
        views: Int = 0,
        likes: Int = 0,
        comments: Int = 0,
        shares: Int = 0,
        watchTimeSeconds: Long = 0,
        subscriberGained: Int = 0,
        uploadId: Long = this.uploadId,
    ) = AnalyticsDaily(
        videoUploadId = uploadId,
        date = LocalDate.of(2026, 8, day),
        views = views,
        likes = likes,
        commentsCount = comments,
        shares = shares,
        watchTimeSeconds = watchTimeSeconds,
        subscriberGained = subscriberGained,
    )

    /**
     * 기본은 이 영상이 채널의 전부인 상황. 채널 기준선도 이 행들에서 나온다.
     *
     * `platforms` 로 업로드별 플랫폼을 지정한다(기본 YouTube — 다섯 지표를 모두 수집).
     * 등장하는 **모든 업로드를 `findByUserId` 에 등록한다.** 등록되지 않은 행은
     * fail-closed 로 빠지는데, 운영에서는 `findByUserId` 가 사용자의 업로드를 전부
     * 돌려주므로 그 상태가 정상적으로 생기지 않는다.
     */
    private fun given(
        rows: List<AnalyticsDaily>,
        channelRows: List<AnalyticsDaily> = rows,
        platforms: Map<Long, Platform> = emptyMap(),
    ) {
        fun uploadFor(id: Long) = VideoUpload(
            id = id,
            // 대상 업로드만 요청 영상에 속한다. 나머지는 각자 다른 영상이어야 비교군이 된다.
            videoId = if (id == uploadId) videoId else 1_000L + id,
            platform = platforms[id] ?: Platform.YOUTUBE,
            channelId = 1L,
        )

        val targetUploadIds = rows.map { it.videoUploadId }.distinct().ifEmpty { listOf(uploadId) }
        val allUploadIds = (rows + channelRows).map { it.videoUploadId }.distinct()
            .ifEmpty { listOf(uploadId) }

        every { videoRepository.findById(videoId) } returns Video(id = videoId, userId = userId, title = "v")
        every { videoUploadRepository.findByVideoId(videoId) } returns targetUploadIds.map { uploadFor(it) }
        every { videoUploadRepository.findByUserId(userId) } returns allUploadIds.map { uploadFor(it) }
        every { analyticsRepository.findByVideoUploadIds(targetUploadIds) } returns rows
        every { analyticsRepository.findAllByUserId(userId) } returns channelRows
    }

    private fun score() = useCase.getPerformanceScore(userId, videoId)

    // ── 조회수 0: 비율의 분모가 없다 ─────────────────────────────────────────

    /** **이 케이스가 "참여율 0점" 을 만들던 자리다.** */
    @Test
    @DisplayName("조회수가 0이면 조회수를 분모로 쓰는 점수를 만들지 않는다")
    fun zeroViewsProducesNoRateScores() {
        given(List(4) { row(day = 20 + it, views = 0) })

        val response = score()

        assertNull(response.breakdown["engagement"], "분모가 없는데 참여율을 매겼다")
        assertNull(response.breakdown["conversion"])
        assertNull(response.breakdown["share"])
        assertTrue(response.unavailableMetrics.containsKey("engagement"))
    }

    /**
     * 조회수가 0 이면 채널 기준선도 없다(이 영상이 채널의 전부이므로). 모든 축이 사라지면
     * 총점도 없다 — `0점` 은 "성과가 나빴다" 는 판정이 된다.
     */
    @Test
    @DisplayName("계산 가능한 하위 점수가 없으면 총점도 만들지 않는다")
    fun noSubscoreMeansNoOverallScore() {
        given(List(4) { row(day = 20 + it, views = 0) })

        val response = score()

        assertNull(response.overallScore, "근거 없는 0점을 지어냈다")
        assertTrue(response.unavailableMetrics.containsKey("overall"))
        // 집계 행 자체는 있었다. 그 사실은 그대로 알린다.
        assertTrue(response.dataAvailable)
    }

    /** 총점이 없으면 순위도 없다. `0` 은 "상위 0%"(최상위)로 읽힌다. */
    @Test
    @DisplayName("총점이 없으면 백분위도 매기지 않는다")
    fun noOverallScoreMeansNoPercentile() {
        given(List(4) { row(day = 20 + it, views = 0) })

        assertNull(score().percentileRank)
    }

    // ── 조회수 0이 4일: 무엇이 측정이고 무엇이 미측정인가 ────────────────────
    //
    // **`analytics_daily.views` 의 0 은 관측이다.**
    //
    // `revenue_micro` 에는 `revenue_status` 라는 수집 상태 컬럼이 따로 있고
    // (`RevenueJooqRepository.MEASURED_ONLY` 가 그것으로 거른다), 시청 시간에는
    // `PlatformMetricAvailability` 의 플랫폼 허용 목록이 있다. **`views` 에는 그 어느
    // 장치도 없다** — 어떤 플랫폼도 views 를 미수집으로 선언하지 않는다. 행이 존재한다는
    // 것 자체가 그 날 수집이 돌았다는 뜻이므로, 그 행의 `views = 0` 은 "0회 조회됐다"
    // 는 관측이다.
    //
    // 그래서 같은 데이터를 두고 축마다 답이 갈린다.
    //
    // - **하위 점수는 비율** → 조회수가 분모다. 분모가 0 이면 값이 정의되지 않아 `null`.
    // - **추세·예측은 수준과 방향** → 나누지 않는다. 0 과 0 을 견주는 데 문제가 없고,
    //   결과인 `"stable"` 과 `0` 은 관측에서 나온 값이다.

    /**
     * **관측된 "변화 없음" 은 버리지 않는다.**
     *
     * 4일 내내 조회수가 0 이면 조회수는 오르지도 내리지도 않았다. 그것은 실제로 본
     * 사실이므로 `"stable"` 이 맞다. 예전 결함은 **기간이 모자랄 때** "stable" 을 지어낸
     * 것이지, 평평한 실측을 "stable" 이라 부른 것이 아니다.
     */
    @Test
    @DisplayName("4일 내내 조회수가 0이면 추세는 측정된 stable 이다")
    fun flatZeroViewsIsAMeasuredStableTrend() {
        given(List(4) { row(day = 20 + it, views = 0) })

        val response = score()

        assertEquals("stable", response.trend, "관측된 '변화 없음'을 버렸다")
        assertTrue("trend" !in response.unavailableMetrics, "측정된 추세를 미측정으로 표시했다")
    }

    /** 회귀선의 기울기 0, 절편 0 → 예측 0. 자리채움이 아니라 모델이 낸 값이다. */
    @Test
    @DisplayName("4일 내내 조회수가 0이면 예측도 측정된 0이다")
    fun flatZeroViewsProducesAMeasuredZeroPrediction() {
        given(List(4) { row(day = 20 + it, views = 0) })

        val response = score()

        assertEquals(0L, response.prediction7d, "관측에서 나온 0을 버렸다")
        assertTrue("prediction7d" !in response.unavailableMetrics)
    }

    /**
     * **같은 데이터에서 축마다 답이 갈리는 것이 옳다.**
     *
     * 비율은 분모가 없어 `null`, 수준·방향은 관측이라 값이 있다. 한쪽에 맞춰 다른 쪽을
     * 지우면 실제 관찰을 버리거나(추세·예측) 없는 판정을 만든다(하위 점수).
     */
    @Test
    @DisplayName("조회수 0에서 비율은 측정 불가지만 추세·예측은 측정값이다")
    fun ratiosAreUnavailableWhileLevelObservationsRemain() {
        given(List(4) { row(day = 20 + it, views = 0) })

        val response = score()

        // 비율: 분모가 없다.
        assertNull(response.breakdown["engagement"])
        assertNull(response.breakdown["conversion"])
        assertNull(response.breakdown["share"])
        assertNull(response.overallScore)
        // 수준·방향: 관측됐다.
        assertNotNull(response.trend)
        assertNotNull(response.prediction7d)
    }

    /**
     * 0 에서 실제로 오른 것도 관측이다. 추세는 방향만 말하므로 0 이 기준선이어도 문제없다 —
     * 퍼센트였다면 [com.ongo.domain.analytics.MetricChange] 처럼 `null` 이어야 한다.
     */
    @Test
    @DisplayName("0에서 조회수가 붙기 시작하면 상승으로 본다")
    fun risingFromZeroIsAnUpTrend() {
        given(listOf(
            row(day = 20, views = 0),
            row(day = 21, views = 0),
            row(day = 22, views = 40),
            row(day = 23, views = 60),
        ))

        assertEquals("up", score().trend)
    }

    // ── 측정된 0 은 보존 ─────────────────────────────────────────────────────

    /**
     * **조회는 있는데 참여가 없으면 그 0 은 실측이다.** 아무도 좋아요를 누르지 않았다는
     * 관측이므로 측정 불가로 감추면 실제 사실을 잃는다.
     */
    @Test
    @DisplayName("조회가 있고 참여가 0이면 참여율 0점을 보존한다")
    fun measuredZeroEngagementIsPreserved() {
        given(List(4) { row(day = 20 + it, views = 1_000) })

        val response = score()

        assertEquals(0.0, response.breakdown["engagement"], "측정된 0을 측정 불가로 감췄다")
        assertTrue("engagement" !in response.unavailableMetrics)
    }

    /** 조회속도도 마찬가지다. 조회가 채널 평균보다 낮은 것은 측정된 사실이다. */
    @Test
    @DisplayName("조회속도가 낮은 것은 측정된 결과이므로 점수를 낸다")
    fun lowViewVelocityIsAMeasuredResult() {
        val target = List(4) { row(day = 20 + it, views = 10) }
        val peer = List(4) { row(day = 20 + it, views = 10_000, uploadId = 202L) }
        given(target, channelRows = target + peer)

        val velocity = score().breakdown["viewVelocity"]

        assertNotNull(velocity)
        assertTrue(velocity < 50.0, "채널 평균보다 훨씬 낮은데 50점 이상이다: $velocity")
    }

    // ── 전환·공유 기준선이 없을 때 ───────────────────────────────────────────

    /**
     * **이 케이스가 임의 기준선 `0.001` 을 쓰던 자리다.**
     *
     * 채널 전체에서 구독 전환이 한 번도 집계되지 않으면 비교할 기준이 없다. 예전에는
     * `0.001`(전환율 0.1%)을 기준으로 삼아, 구독자 1명만 늘어도 100점이 나왔다.
     */
    @Test
    @DisplayName("채널 전환 기준선이 없으면 전환 점수를 만들지 않는다")
    fun noConversionBaselineProducesNoConversionScore() {
        given(List(4) { row(day = 20 + it, views = 1_000, subscriberGained = 0) })

        val response = score()

        assertNull(response.breakdown["conversion"], "기준선 없이 전환 점수를 매겼다")
        assertEquals(
            PerformanceScoreUseCase.REASON_NO_CONVERSION_BASELINE,
            response.unavailableMetrics["conversion"],
        )
    }

    @Test
    @DisplayName("채널 공유 기준선이 없으면 공유 점수를 만들지 않는다")
    fun noShareBaselineProducesNoShareScore() {
        given(List(4) { row(day = 20 + it, views = 1_000, shares = 0) })

        val response = score()

        assertNull(response.breakdown["share"])
        assertEquals(PerformanceScoreUseCase.REASON_NO_SHARE_BASELINE, response.unavailableMetrics["share"])
    }

    @Test
    @DisplayName("기준선이 있으면 전환·공유 점수를 계산한다")
    fun baselinePresentProducesScores() {
        given(List(4) { row(day = 20 + it, views = 1_000, shares = 10, subscriberGained = 5) })

        val response = score()

        // 이 영상이 채널의 전부이므로 기준선과 정확히 같다 → 50점.
        assertEquals(50.0, response.breakdown["conversion"])
        assertEquals(50.0, response.breakdown["share"])
    }

    // ── 시청 시간: YouTube 만 수집한다 ───────────────────────────────────────

    /**
     * **이 케이스가 총점의 20% 를 상시로 깎던 자리다.**
     *
     * 시청 시간을 주는 어댑터는 YouTube 하나뿐이다. 다른 플랫폼만 쓰는 크리에이터는
     * 채널 평균이 0 → `coerceAtLeast(1.0)` → 시청시간 0점이 됐고, 그 0 이 20% 가중치로
     * 총점에 들어갔다.
     */
    @Test
    @DisplayName("시청 시간이 수집되지 않으면 시청시간 점수를 만들지 않는다")
    fun noWatchTimeProducesNoWatchTimeScore() {
        given(List(4) { row(day = 20 + it, views = 1_000, watchTimeSeconds = 0) })

        val response = score()

        assertNull(response.breakdown["watchTime"], "수집되지 않은 시청시간을 0점으로 판정했다")
        assertEquals(PerformanceScoreUseCase.REASON_NO_WATCH_TIME, response.unavailableMetrics["watchTime"])
    }

    /**
     * 그리고 그 0 이 총점을 끌어내리지 않아야 한다. **측정된 축만으로 평균을 내고
     * 그 가중치 합으로 다시 정규화한다.**
     */
    @Test
    @DisplayName("미측정 축은 총점의 분모에서도 빠진다")
    fun unmeasuredAxesAreExcludedFromTheOverallDenominator() {
        // 참여율만 계산 가능한 상황을 만든다: 조회수는 있고 시청시간·전환·공유는 없다.
        // 조회속도는 채널 기준선이 있으므로 계산된다.
        given(List(4) { row(day = 20 + it, views = 1_000, likes = 100) })

        val response = score()
        val velocity = response.breakdown["viewVelocity"]!!
        val engagement = response.breakdown["engagement"]!!

        // 측정된 축은 조회속도(0.30)와 참여율(0.25) 둘뿐 → 가중치 합 0.55 로 정규화.
        val expected = (velocity * 0.30 + engagement * 0.25) / 0.55
        assertEquals(expected, response.overallScore!!, 0.05, "미측정 축을 0으로 넣어 총점을 깎았다")
    }

    // ── 짧은 기간: 추세와 예측 ───────────────────────────────────────────────

    /** **이 케이스가 "안정적 추세" 를 지어내던 자리다.** */
    @Test
    @DisplayName("기간이 짧으면 추세를 안정적이라고 말하지 않는다")
    fun shortHistoryHasNoTrend() {
        given(listOf(row(day = 20, views = 500), row(day = 21, views = 600)))

        val response = score()

        assertNull(response.trend, "관측한 적 없는 추세를 말했다")
        assertEquals(PerformanceScoreUseCase.REASON_TREND_TOO_SHORT, response.unavailableMetrics["trend"])
    }

    /**
     * **이 케이스가 관측 합계를 예측으로 둔갑시키던 자리다.**
     *
     * 하루치만 있으면 `last7.sumOf { views }` 를 돌려줬고 화면은 그것을 "7일 예상
     * 조회수"로 그렸다. 어제 500회면 "앞으로 7일간 500회 예상"이 됐다.
     */
    @Test
    @DisplayName("관측일이 하루뿐이면 관측 합계를 예측으로 내보내지 않는다")
    fun singleDayDoesNotBecomeAPrediction() {
        given(listOf(row(day = 20, views = 500)))

        val response = score()

        assertNull(response.prediction7d, "관측 합계를 7일 예측으로 내보냈다")
        assertEquals(
            PerformanceScoreUseCase.REASON_PREDICTION_TOO_SHORT,
            response.unavailableMetrics["prediction7d"],
        )
    }

    /**
     * **점 2~3개로는 예측하지 않는다.**
     *
     * 점이 둘이면 직선이 **항상 완전적합**(잔차 0)이라 아무 두 점이나 이어도 결정계수가
     * 1 이 나온다. 적합도가 추세의 근거가 되지 못하므로, 그 선을 7일 앞으로 늘린 값을
     * "예상 조회수"라고 부를 수 없다. 점 3개도 자유도가 1 뿐이다.
     */
    @Test
    @DisplayName("관측일이 2~3일이면 예측을 만들지 않는다")
    fun twoOrThreeDaysIsNotEnoughToPredict() {
        listOf(2, 3).forEach { days ->
            given(List(days) { row(day = 20 + it, views = 100 * (it + 1)) })

            val response = score()

            assertNull(response.prediction7d, "${days}일 관측으로 예측을 만들었다")
            assertEquals(
                PerformanceScoreUseCase.REASON_PREDICTION_TOO_SHORT,
                response.unavailableMetrics["prediction7d"],
                "${days}일: 사유가 다르다",
            )
        }
    }

    /**
     * 추세와 예측은 **같은 관측일 요건**을 쓴다. 서로 다르면 "추세는 판단 불가인데 예측은
     * 있다" 같은 어긋난 조합이 화면에 나온다.
     */
    @Test
    @DisplayName("추세와 예측의 최소 관측일이 같다")
    fun trendAndPredictionShareTheSameMinimumHistory() {
        // 3일: 둘 다 없어야 한다.
        given(List(3) { row(day = 20 + it, views = 100 * (it + 1)) })
        val short = score()
        assertNull(short.trend)
        assertNull(short.prediction7d)

        // 4일: 둘 다 있어야 한다.
        given(List(4) { row(day = 20 + it, views = 100 * (it + 1)) })
        val enough = score()
        assertNotNull(enough.trend)
        assertNotNull(enough.prediction7d)
    }

    @Test
    @DisplayName("관측일이 충분하면 추세와 예측을 계산한다")
    fun enoughHistoryProducesTrendAndPrediction() {
        given(listOf(
            row(day = 20, views = 100, likes = 10, shares = 1, subscriberGained = 1, watchTimeSeconds = 1_000),
            row(day = 21, views = 200, likes = 20, shares = 2, subscriberGained = 2, watchTimeSeconds = 2_000),
            row(day = 22, views = 300, likes = 30, shares = 3, subscriberGained = 3, watchTimeSeconds = 3_000),
            row(day = 23, views = 400, likes = 40, shares = 4, subscriberGained = 4, watchTimeSeconds = 4_000),
        ))

        val response = score()

        assertEquals("up", response.trend)
        assertNotNull(response.prediction7d)
        assertTrue(response.prediction7d!! > 0)
        // 다섯 축 모두 측정 가능한 상황이다.
        assertTrue(response.breakdown.values.all { it != null }, "측정 가능한데 비웠다: ${response.breakdown}")
        assertNotNull(response.overallScore)
        assertTrue(response.unavailableMetrics.keys.none { it in setOf("overall", "trend", "prediction7d") })
    }

    // ── 사유는 값과 어긋나지 않는다 ──────────────────────────────────────────

    /**
     * `null` 인데 사유가 없으면 화면은 이유 없이 빈 칸만 그린다. 둘은 항상 같이 움직여야 한다.
     */
    @Test
    @DisplayName("null인 하위 점수는 반드시 사유를 갖는다")
    fun everyNullSubscoreHasAReason() {
        given(List(4) { row(day = 20 + it, views = 1_000) })

        val response = score()

        response.breakdown.forEach { (key, value) ->
            assertEquals(
                value == null, key in response.unavailableMetrics,
                "$key: 값과 사유가 어긋난다 (value=$value)",
            )
        }
    }

    /** 사유는 숫자가 아니라 문장이어야 한다. 숫자를 넣으면 그것이 점수로 읽힌다. */
    @Test
    @DisplayName("측정 불가 사유에 숫자가 들어가지 않는다")
    fun reasonsAreSentencesNotNumbers() {
        val reasons = listOf(
            PerformanceScoreUseCase.REASON_NO_CHANNEL_VIEW_BASELINE,
            PerformanceScoreUseCase.REASON_NO_VIEWS,
            PerformanceScoreUseCase.REASON_NO_WATCH_TIME,
            PerformanceScoreUseCase.REASON_NO_CONVERSION_BASELINE,
            PerformanceScoreUseCase.REASON_NO_SHARE_BASELINE,
            PerformanceScoreUseCase.REASON_NO_SUBSCORE,
            PerformanceScoreUseCase.REASON_TREND_TOO_SHORT,
            PerformanceScoreUseCase.REASON_PREDICTION_TOO_SHORT,
            PerformanceScoreUseCase.REASON_NO_COMPARABLE_PEERS,
        )
        reasons.forEach { reason ->
            assertTrue(reason.isNotBlank())
            assertTrue(!Regex("[0-9]").containsMatchIn(reason), "사유에 숫자가 있다: $reason")
        }
    }

    // ── 비교 모집단은 같은 척도끼리 ──────────────────────────────────────────

    /**
     * {조회속도, 참여율} 두 축으로 낸 점수와 다섯 축 전부로 낸 점수는 척도가 다르다.
     * 섞어서 순위를 매기면 "측정된 지표가 적은 영상이 유리하다" 같은 일이 벌어진다.
     */
    @Test
    @DisplayName("축 집합이 다른 영상과는 순위를 비교하지 않는다")
    fun peersWithDifferentMetricSetsAreNotCompared() {
        /*
         * 대상 영상은 아직 조회수가 잡히지 않았다 → 조회수를 분모로 쓰는 세 축(참여율·
         * 전환·공유)이 없다. 비교 대상 둘은 다섯 축 전부 측정됐다.
         *
         * 두 점수는 서로 다른 축으로 만들어졌으므로 같은 자로 잰 값이 아니다.
         */
        val target = List(4) { row(day = 20 + it, views = 0, watchTimeSeconds = 100) }
        val peerA = List(4) { row(day = 20 + it, views = 1_000, likes = 50, shares = 5, subscriberGained = 2, watchTimeSeconds = 5_000, uploadId = 202L) }
        val peerB = List(4) { row(day = 20 + it, views = 900, likes = 40, shares = 4, subscriberGained = 2, watchTimeSeconds = 4_000, uploadId = 203L) }

        every { videoRepository.findById(videoId) } returns Video(id = videoId, userId = userId, title = "v")
        every { videoUploadRepository.findByVideoId(videoId) } returns listOf(
            VideoUpload(id = uploadId, videoId = videoId, platform = Platform.YOUTUBE, channelId = 1L),
        )
        every { videoUploadRepository.findByUserId(userId) } returns listOf(
            VideoUpload(id = uploadId, videoId = videoId, platform = Platform.YOUTUBE, channelId = 1L),
            VideoUpload(id = 202L, videoId = 43L, platform = Platform.YOUTUBE, channelId = 1L),
            VideoUpload(id = 203L, videoId = 44L, platform = Platform.YOUTUBE, channelId = 1L),
        )
        every { analyticsRepository.findByVideoUploadIds(listOf(uploadId)) } returns target
        every { analyticsRepository.findAllByUserId(userId) } returns target + peerA + peerB

        val response = score()

        // 대상은 {조회속도, 시청시간} 두 축, 비교군은 다섯 축 → 같은 척도의 상대가 없다.
        assertNull(response.breakdown["engagement"], "분모가 없는데 참여율을 매겼다")
        assertNotNull(response.overallScore, "측정된 축이 있는데 총점을 비웠다")
        assertNull(response.percentileRank, "척도가 다른 영상과 순위를 비교했다")
        assertEquals(
            PerformanceScoreUseCase.REASON_NO_COMPARABLE_PEERS,
            response.unavailableMetrics["percentileRank"],
        )
    }

    // ══ 플랫폼 가용성 ═══════════════════════════════════════════════════════
    //
    // `analytics_daily` 는 숫자 컬럼이라 **어댑터가 수집하지 않는 지표도 0 으로 저장된다.**
    // 13개 어댑터 중 시청 시간·구독 증가를 실제로 조회하는 것은 `YouTubeClient` 하나뿐이고,
    // 공유는 Facebook·WordPress·Vimeo 가, 댓글은 Pinterest 가 주지 않는다.
    //
    // 그 0 을 관측값으로 섞으면 두 곳이 한꺼번에 무너진다 — **점수**(분자에 들어가 낮아짐)
    // 와 **기준선**(분모에 들어가 채널 평균이 낮아짐). 기준선이 낮으면 비교 대상 전체가
    // 실제보다 좋아 보이므로 순위까지 틀어진다.

    /** **Facebook 은 공유를 주지 않는다.** 그 0 이 공유 축에 섞이면 안 된다. */
    @Test
    @DisplayName("Facebook 의 공유 0은 공유 점수에도 기준선에도 섞이지 않는다")
    fun facebookSharesAreNotMistakenForZero() {
        given(
            rows = List(4) { row(day = 20 + it, views = 1_000, shares = 0) },
            platforms = mapOf(uploadId to Platform.FACEBOOK),
        )

        val response = score()

        assertNull(response.breakdown["share"], "수집하지 않는 공유 0으로 점수를 매겼다")
        assertEquals(
            PerformanceScoreUseCase.REASON_SHARES_NOT_COLLECTED,
            response.unavailableMetrics["share"],
            "기준선 부족이 아니라 플랫폼 미수집이라고 말해야 한다",
        )
    }

    /**
     * 혼합 채널에서 **Facebook 의 0 이 YouTube 의 공유 기준선을 끌어내리면 안 된다.**
     *
     * YouTube 영상은 조회 1,000 당 공유 10 (1%). Facebook 행이 기준선에 섞이면 채널
     * 평균이 0.5% 로 반토막 나고, 같은 실적의 YouTube 영상이 100점을 받게 된다.
     */
    @Test
    @DisplayName("혼합 채널에서 Facebook 행이 공유 기준선을 낮추지 않는다")
    fun facebookRowsDoNotDeflateTheShareBaseline() {
        val youtube = List(4) { row(day = 20 + it, views = 1_000, shares = 10) }
        val facebook = List(4) { row(day = 20 + it, views = 1_000, shares = 0, uploadId = 202L) }
        given(
            rows = youtube,
            channelRows = youtube + facebook,
            platforms = mapOf(uploadId to Platform.YOUTUBE, 202L to Platform.FACEBOOK),
        )

        // 기준선이 YouTube 행만으로 만들어지면 이 영상은 기준선과 정확히 같아 50점이다.
        assertEquals(50.0, score().breakdown["share"], "Facebook 의 0이 기준선에 섞였다")
    }

    /** **Instagram 은 구독 증가를 주지 않는다.** */
    @Test
    @DisplayName("Instagram 의 구독 0은 전환 점수에도 기준선에도 섞이지 않는다")
    fun instagramSubscribersAreNotMistakenForZero() {
        given(
            rows = List(4) { row(day = 20 + it, views = 1_000, subscriberGained = 0) },
            platforms = mapOf(uploadId to Platform.INSTAGRAM),
        )

        val response = score()

        assertNull(response.breakdown["conversion"], "수집하지 않는 구독 0으로 점수를 매겼다")
        assertEquals(
            PerformanceScoreUseCase.REASON_SUBSCRIBER_NOT_COLLECTED,
            response.unavailableMetrics["conversion"],
        )
    }

    @Test
    @DisplayName("혼합 채널에서 Instagram 행이 전환 기준선을 낮추지 않는다")
    fun instagramRowsDoNotDeflateTheConversionBaseline() {
        val youtube = List(4) { row(day = 20 + it, views = 1_000, subscriberGained = 5) }
        val instagram = List(4) { row(day = 20 + it, views = 1_000, subscriberGained = 0, uploadId = 202L) }
        given(
            rows = youtube,
            channelRows = youtube + instagram,
            platforms = mapOf(uploadId to Platform.YOUTUBE, 202L to Platform.INSTAGRAM),
        )

        assertEquals(50.0, score().breakdown["conversion"], "Instagram 의 0이 기준선에 섞였다")
    }

    /**
     * **참여율은 좋아요 + 댓글 + 공유 전체가 정의다.**
     *
     * Pinterest 는 댓글을 주지 않으므로 그 행의 합계는 정의대로 계산된 값이 아니다.
     * 분자만 빼고 조회수를 분모에 남기면 참여율이 실제보다 낮아지므로 **행 자체를 뺀다.**
     */
    @Test
    @DisplayName("Pinterest 는 댓글을 주지 않아 참여율 행에서 제외한다")
    fun pinterestRowsAreExcludedFromEngagement() {
        given(
            rows = List(4) { row(day = 20 + it, views = 1_000, likes = 100, comments = 0, shares = 10) },
            platforms = mapOf(uploadId to Platform.PINTEREST),
        )

        val response = score()

        assertNull(response.breakdown["engagement"], "댓글이 빠진 합계로 참여율을 매겼다")
        assertEquals(
            PerformanceScoreUseCase.REASON_ENGAGEMENT_NOT_COLLECTED,
            response.unavailableMetrics["engagement"],
        )
    }

    /**
     * 혼합 영상에서 Pinterest 행이 참여율 **분모에도** 남으면 안 된다.
     *
     * YouTube 행만 보면 100/1,000 = 10% → 100점. Pinterest 조회수가 분모에 남으면
     * 5% → 50점으로 반토막 난다.
     */
    @Test
    @DisplayName("혼합 영상에서 Pinterest 조회수가 참여율 분모에 남지 않는다")
    fun pinterestViewsDoNotStayInTheEngagementDenominator() {
        given(
            rows = List(4) { row(day = 20 + it, views = 1_000, likes = 100, comments = 0, shares = 0) } +
                List(4) { row(day = 20 + it, views = 1_000, likes = 0, comments = 0, shares = 0, uploadId = 202L) },
            platforms = mapOf(uploadId to Platform.YOUTUBE, 202L to Platform.PINTEREST),
        )

        assertEquals(100.0, score().breakdown["engagement"], "Pinterest 조회수가 분모에 남았다")
    }

    /**
     * **TikTok 의 시청 시간 0 이 YouTube 실측을 희석하면 안 된다.**
     *
     * 같은 영상을 두 곳에 올리면 예전에는 평균의 분모가 8행(양쪽 전부)이라 YouTube 의
     * 실측이 정확히 절반으로 떨어졌다.
     */
    @Test
    @DisplayName("혼합 영상에서 TikTok 행이 시청 시간을 희석하지 않는다")
    fun tiktokRowsDoNotDiluteWatchTime() {
        /*
         * 채널에 **다른 영상**을 하나 더 둬야 이 테스트가 판별력을 갖는다. 대상 영상만
         * 있으면 분자와 분모가 똑같이 희석돼 비율이 그대로라, 필터가 없어도 통과한다.
         */
        val targetYoutube = List(4) { row(day = 20 + it, views = 1_000, watchTimeSeconds = 10_000) }
        val targetTiktok = List(4) { row(day = 20 + it, views = 1_000, watchTimeSeconds = 0, uploadId = 202L) }
        val peerYoutube = List(4) { row(day = 20 + it, views = 1_000, watchTimeSeconds = 10_000, uploadId = 203L) }

        given(
            rows = targetYoutube + targetTiktok,
            channelRows = targetYoutube + targetTiktok + peerYoutube,
            platforms = mapOf(
                uploadId to Platform.YOUTUBE,
                202L to Platform.TIKTOK,
                203L to Platform.YOUTUBE,
            ),
        )

        /*
         * 시청 시간 수집 행만 보면 이 영상의 평균(10,000)과 채널 평균(10,000)이 같아 50점.
         * TikTok 행이 섞이면 영상 평균은 5,000, 채널 평균은 6,666.7 이 되어 37.5점이 된다.
         */
        assertEquals(50.0, score().breakdown["watchTime"], "TikTok 의 0이 시청 시간에 섞였다")
    }

    /** TikTok 만 쓰는 영상은 시청 시간을 물어볼 곳이 없다. */
    @Test
    @DisplayName("시청 시간을 수집하지 않는 플랫폼뿐이면 사유를 구분해 말한다")
    fun tiktokOnlyVideoReportsWatchTimeNotCollected() {
        given(
            rows = List(4) { row(day = 20 + it, views = 1_000, watchTimeSeconds = 0) },
            platforms = mapOf(uploadId to Platform.TIKTOK),
        )

        val response = score()

        assertNull(response.breakdown["watchTime"])
        assertEquals(
            PerformanceScoreUseCase.REASON_WATCH_TIME_NOT_COLLECTED,
            response.unavailableMetrics["watchTime"],
        )
    }

    /** **지원 플랫폼의 실제 0 은 보존한다.** YouTube 는 공유를 수집한다. */
    @Test
    @DisplayName("지원 플랫폼에서 관측된 0은 그대로 점수에 반영한다")
    fun measuredZeroOnASupportingPlatformIsPreserved() {
        val target = List(4) { row(day = 20 + it, views = 1_000, shares = 0) }
        val peer = List(4) { row(day = 20 + it, views = 1_000, shares = 20, uploadId = 202L) }
        given(
            rows = target,
            channelRows = target + peer,
            platforms = mapOf(uploadId to Platform.YOUTUBE, 202L to Platform.YOUTUBE),
        )

        val response = score()

        // 공유를 수집하는 플랫폼에서 실제로 0회 공유됐다 → 측정된 0점.
        assertEquals(0.0, response.breakdown["share"], "실측 0을 측정 불가로 감췄다")
        assertTrue("share" !in response.unavailableMetrics)
    }

    /** YouTube 는 다섯 지표를 모두 수집한다 — 회귀 확인. */
    @Test
    @DisplayName("YouTube 단독 영상은 다섯 축을 모두 계산한다")
    fun youtubeOnlyVideoMeasuresEveryAxis() {
        given(
            rows = List(4) {
                row(day = 20 + it, views = 1_000, likes = 50, comments = 10, shares = 5, watchTimeSeconds = 5_000, subscriberGained = 3)
            },
            platforms = mapOf(uploadId to Platform.YOUTUBE),
        )

        val response = score()

        assertTrue(response.breakdown.values.all { it != null }, "축이 비었다: ${response.breakdown}")
        assertNotNull(response.overallScore)
    }

    /**
     * 혼합 플랫폼 영상의 축 집합은 **지원 플랫폼의 합집합**이다. YouTube 가 섞여 있으면
     * 시청 시간·구독 전환도 계산된다.
     */
    @Test
    @DisplayName("혼합 영상은 지원 플랫폼이 있는 축을 모두 계산한다")
    fun mixedPlatformVideoMeasuresEveryAxisWithAnySupportingRow() {
        given(
            rows = List(4) { row(day = 20 + it, views = 1_000, likes = 50, comments = 10, shares = 5, watchTimeSeconds = 5_000, subscriberGained = 3) } +
                List(4) { row(day = 20 + it, views = 2_000, likes = 100, comments = 20, shares = 0, watchTimeSeconds = 0, subscriberGained = 0, uploadId = 202L) },
            platforms = mapOf(uploadId to Platform.YOUTUBE, 202L to Platform.FACEBOOK),
        )

        val response = score()

        // Facebook 은 공유를 주지 않지만 YouTube 행이 있으므로 공유 축은 계산된다.
        assertNotNull(response.breakdown["share"])
        assertNotNull(response.breakdown["watchTime"])
        assertNotNull(response.breakdown["conversion"])
        // 참여율은 YouTube 행만으로 계산된다(Facebook 행은 공유가 빠져 제외).
        assertNotNull(response.breakdown["engagement"])
    }

    /** 같은 축으로 측정된 상대가 충분하면 순위를 매긴다. */
    @Test
    @DisplayName("같은 축으로 측정된 비교 대상이 있으면 순위를 매긴다")
    fun peersWithTheSameMetricSetAreCompared() {
        fun full(uploadId: Long, views: Int) = List(4) {
            row(day = 20 + it, views = views, likes = views / 20, shares = 5, subscriberGained = 2, watchTimeSeconds = 5_000, uploadId = uploadId)
        }
        val target = full(uploadId, 1_000)
        val peerA = full(202L, 5_000)
        val peerB = full(203L, 100)

        every { videoRepository.findById(videoId) } returns Video(id = videoId, userId = userId, title = "v")
        every { videoUploadRepository.findByVideoId(videoId) } returns listOf(
            VideoUpload(id = uploadId, videoId = videoId, platform = Platform.YOUTUBE, channelId = 1L),
        )
        every { videoUploadRepository.findByUserId(userId) } returns listOf(
            VideoUpload(id = uploadId, videoId = videoId, platform = Platform.YOUTUBE, channelId = 1L),
            VideoUpload(id = 202L, videoId = 43L, platform = Platform.YOUTUBE, channelId = 1L),
            VideoUpload(id = 203L, videoId = 44L, platform = Platform.YOUTUBE, channelId = 1L),
        )
        every { analyticsRepository.findByVideoUploadIds(listOf(uploadId)) } returns target
        every { analyticsRepository.findAllByUserId(userId) } returns target + peerA + peerB

        val response = score()

        assertNotNull(response.percentileRank, "같은 축으로 측정된 상대가 셋인데 순위를 비웠다")
        assertTrue("percentileRank" !in response.unavailableMetrics)
    }

    // ══ 이름이 다른 지표를 잘못 매핑한 경우 ═════════════════════════════════
    //
    // 하드코딩 0 만 문제가 아니다. **다른 뜻의 숫자를 그 축의 값으로 쓰던 자리**가 셋 있었다.
    // 값이 0 이 아니라 커다란 실수라, 미수집보다 더 조용히 틀린다.

    /**
     * **Pinterest 의 `shares` 는 공유가 아니라 클릭 수였다.**
     *
     * `PinterestClient.kt:160` 이 `shares = metrics["PIN_CLICK"]` 이다. PIN_CLICK 은
     * 요청한 metricTypes 중 하나로 핀을 **클릭한 횟수**다. 클릭은 공유보다 훨씬 자주
     * 일어나므로 그대로 두면 공유율이 부풀고 공유 급등 오판으로 이어진다.
     */
    @Test
    @DisplayName("Pinterest 의 클릭 수를 공유 점수로 쓰지 않는다")
    fun pinterestClickCountIsNotUsedAsShares() {
        given(
            rows = List(4) { row(day = 20 + it, views = 1_000, shares = 400) },
            platforms = mapOf(uploadId to Platform.PINTEREST),
        )

        val response = score()

        assertNull(response.breakdown["share"], "클릭 수로 공유 점수를 매겼다")
        assertEquals(
            PerformanceScoreUseCase.REASON_SHARES_NOT_COLLECTED,
            response.unavailableMetrics["share"],
        )
    }

    /**
     * **Dailymotion 의 `shares` 는 공유가 아니라 북마크 수였다.**
     *
     * `DailymotionClient.kt:121` 이 `shares = response.bookmarksTotal` 이고 요청 필드는
     * `bookmarks_total`(`:113`) 이다. 북마크(즐겨찾기)는 공유가 아니며, 요청 필드 목록에
     * 공유 수는 아예 없다.
     */
    @Test
    @DisplayName("Dailymotion 의 북마크 수를 공유 점수로 쓰지 않는다")
    fun dailymotionBookmarksAreNotUsedAsShares() {
        given(
            rows = List(4) { row(day = 20 + it, views = 1_000, shares = 300) },
            platforms = mapOf(uploadId to Platform.DAILYMOTION),
        )

        assertNull(score().breakdown["share"], "북마크 수로 공유 점수를 매겼다")
    }

    /**
     * **Tumblr 의 `views` 는 조회수가 아니라 노트 총합이었다.**
     *
     * `TumblrClient.kt:141` 이 `views = response.response?.totalNotes` 다. `total_notes` 는
     * 좋아요 + 리블로그 + 답글의 합이고(`TumblrDtos.kt:66`), 같은 응답의 노트 목록에서
     * 그 셋을 따로 세어 likes·shares·comments 에 넣는다. **참여율의 분자와 분모가 거의
     * 같은 수가 되어 모든 Tumblr 글이 참여율 100% 근처로 보였다.**
     *
     * Tumblr 공개 API 에는 글 조회수가 없다 — 분모가 없으므로 비율 축이 성립하지 않는다.
     */
    @Test
    @DisplayName("Tumblr 의 노트 총합을 조회수로 쓰지 않는다")
    fun tumblrNoteCountIsNotUsedAsViews() {
        given(
            rows = List(4) { row(day = 20 + it, views = 100, likes = 60, comments = 20, shares = 20) },
            platforms = mapOf(uploadId to Platform.TUMBLR),
        )

        val response = score()

        // 조회수가 없으면 비율 축이 전부 성립하지 않는다.
        assertNull(response.breakdown["engagement"], "노트 총합을 분모로 참여율을 매겼다")
        assertNull(response.breakdown["viewVelocity"], "노트 총합을 조회수로 썼다")
        assertNull(response.overallScore, "근거 없는 총점을 냈다")
    }

    /** 혼합 채널에서 Tumblr 행이 조회수 기준선을 오염시키면 안 된다. */
    @Test
    @DisplayName("Tumblr 행은 채널 조회수 기준선에 섞이지 않는다")
    fun tumblrRowsDoNotContaminateTheViewBaseline() {
        val youtube = List(4) { row(day = 20 + it, views = 1_000) }
        val tumblr = List(4) { row(day = 20 + it, views = 100_000, uploadId = 202L) }
        given(
            rows = youtube,
            channelRows = youtube + tumblr,
            platforms = mapOf(uploadId to Platform.YOUTUBE, 202L to Platform.TUMBLR),
        )

        // YouTube 행만 기준선이면 이 영상은 기준선의 2배(첫 이틀 합계) → 100점.
        // Tumblr 의 노트 총합이 섞이면 기준선이 치솟아 점수가 크게 낮아진다.
        assertEquals(100.0, score().breakdown["viewVelocity"], "Tumblr 노트 총합이 기준선에 섞였다")
    }
}
