package com.ongo.application.analytics

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.analytics.dto.LiveDashboardStateResponse
import com.ongo.application.analytics.dto.LiveMetricResponse
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.LiveAlertConfigRepository
import com.ongo.domain.analytics.LiveAlertRepository
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.revenue.PlatformRevenueStatusCount
import com.ongo.domain.revenue.RevenueRepository
import com.ongo.domain.analytics.RevenueStatus
import com.ongo.common.enums.Platform
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 라이브 대시보드 지표의 **증감률 계약**을 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * `buildMetrics` 는 이렇게 채웠다.
 *
 * ```
 * if (previous == 0L) { if (current > 0) 100.0 else 0.0 }
 * ```
 *
 * `previous = 0` 에서 증감률은 **정의되지 않는다** — 0 으로 나누기 때문이다. 그 자리에
 * `100.0` 을 넣어 첫 조회수 50,000 을 낸 채널과 100 → 200 으로 는 채널이 화면에서
 * 똑같이 "▲100%" 로 보였다. `0 → 0` 은 `0.0` + `STABLE` 이 되어, **데이터가 없다는
 * 사실이 "변화 없음"이라는 측정 결과**로 둔갑했다.
 *
 * 대시보드·수익 화면에서 이미 쓰는 [com.ongo.domain.analytics.MetricChange] 정책을
 * 그대로 재사용한다. 기준이 없으면 `null` 이다.
 *
 * ## 왜 여기서 고정하나
 *
 * `GET /api/v1/analytics/live` 는 노출돼 있지만 **프론트엔드 소비자가 없다**
 * (`frontend/src` 에서 `analytics/live`·`LiveMetric` 검색 0 건). 화면 테스트로 잡히지
 * 않으므로 계약이 조용히 되돌아갈 수 있다. private `buildMetrics` 를 반사로 찌르는 대신
 * **public [LiveDashboardUseCase.getLiveState]** 를 mock repository 로 통과시켜
 * 실제 응답을 검사한다.
 */
class LiveDashboardUseCaseTest {

    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val liveAlertRepository = mockk<LiveAlertRepository>()
    private val liveAlertConfigRepository = mockk<LiveAlertConfigRepository>()
    private val channelRepository = mockk<ChannelRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val revenueRepository = mockk<RevenueRepository>()

    private val useCase = LiveDashboardUseCase(
        analyticsRepository = analyticsRepository,
        liveAlertRepository = liveAlertRepository,
        liveAlertConfigRepository = liveAlertConfigRepository,
        channelRepository = channelRepository,
        videoUploadRepository = videoUploadRepository,
        revenueRepository = revenueRepository,
    )

    /**
     * 기존 계약 테스트는 **여섯 지표가 모두 측정 가능한** 상황을 전제한다.
     * YouTube 는 여섯 지표를 모두 조회하고, 수익은 실측 행이 있어야 열린다.
     */
    private fun givenAllMetricsCollectable() {
        every { videoUploadRepository.findByUserId(userId) } returns listOf(
            VideoUpload(id = 1L, videoId = 1L, platform = Platform.YOUTUBE, channelId = 1L),
        )
        every { revenueRepository.getRevenueStatusCounts(userId, any(), any()) } returns listOf(
            PlatformRevenueStatusCount("YOUTUBE", RevenueStatus.MEASURED.name, 1L),
        )
    }

    private val userId = 7L

    /**
     * 여섯 지표가 모두 같은 값을 갖는 **하루치 측정 행**.
     *
     * 예전에는 `DailyAggregate`(플랫폼 없는 날짜별 합계)를 넣었지만, 유스케이스가
     * 행마다 플랫폼을 확인하도록 바뀌어 원시 행을 그대로 준다. 수익은 행 단위
     * `revenueStatus` 까지 봐야 하므로 실측으로 둔다.
     */
    private fun row(date: LocalDate, value: Long) = AnalyticsDaily(
        videoUploadId = 1L,
        date = date,
        views = value.toInt(),
        likes = value.toInt(),
        commentsCount = value.toInt(),
        shares = value.toInt(),
        watchTimeSeconds = value,
        subscriberGained = value.toInt(),
        revenueMicro = value,
        revenueStatus = RevenueStatus.MEASURED,
    )

    /**
     * 어제 → 오늘 두 행만 주고 `getLiveState` 를 통과시킨다.
     *
     * 자정을 넘겨도 안전하다. 오늘 행이 없으면 유스케이스가 측정된 마지막 날짜를
     * 현재로, **그보다 앞선** 마지막 날짜를 이전으로 잡아 같은 결과가 나온다.
     */
    private fun metricsFor(previous: Long, current: Long): List<LiveMetricResponse> {
        val today = LocalDate.now()
        return stateFor(listOf(row(today.minusDays(1), previous), row(today, current))).metrics
    }

    private fun views(previous: Long, current: Long): LiveMetricResponse =
        metricsFor(previous, current).single { it.type == "VIEWS" }

    /** 임의의 행 목록으로 상태 전체를 만든다. 무데이터 계약 검증용. */
    private fun stateFor(rows: List<AnalyticsDaily>): LiveDashboardStateResponse {
        every { analyticsRepository.findByVideoUploadIdsAndDateRange(any(), any(), any()) } returns
            rows.groupBy { it.videoUploadId }
        every { liveAlertRepository.findByUserId(userId) } returns emptyList()
        every { channelRepository.findByUserId(userId) } returns emptyList()
        givenAllMetricsCollectable()
        return useCase.getLiveState(userId)
    }

    // ── 데이터 부재와 측정된 0 의 구분 ───────────────────────────────────────
    //
    // 예전에는 둘이 **완전히 같은 응답**이었다. 분석 행이 한 줄도 없는 신규 사용자와
    // 어제 실제로 0 건을 기록한 사용자가 똑같이 `currentValue = 0` 을 받았다.
    // 합성 `DailyAggregate(today, 0, ...)` 는 측정된 적이 없는 자리 채우기인데
    // 응답에서는 측정값과 구분되지 않았다.
    //
    // 그 합성 행은 이제 만들지 않는다. 측정 행이 없으면 값 자체가 `null` 이다.

    @Test
    @DisplayName("측정된 행이 하나도 없으면 dataAvailable=false 다")
    fun emptyAggregatesReportNoData() {
        val state = stateFor(emptyList())

        assertEquals(false, state.dataAvailable)
    }

    /**
     * 지표 카드 자체는 남긴다(하위 호환). 대신 **값이 `null`** 이어야 한다.
     *
     * 예전에는 여기서 합성 `0` 이 나갔고 `dataAvailable=false` 만이 그 사실을 알렸다.
     * 지금은 카드 자체가 "재지 않았다"를 들고 있어, 플래그를 안 보는 클라이언트도
     * 0 건과 헷갈릴 수 없다. 여기서 `0L` 이 나오면 합성 값이 되살아난 것이다.
     */
    @Test
    @DisplayName("무데이터의 합성 0을 측정값처럼 표시하지 않는다")
    fun syntheticZeroIsNotPresentedAsMeasured() {
        val state = stateFor(emptyList())

        assertEquals(false, state.dataAvailable, "합성 0 이 측정값으로 나갔다")
        assertTrue(
            state.metrics.all { it.currentValue == null && it.previousValue == null },
            "측정 행이 없는데 값이 나갔다: ${state.metrics.filter { it.currentValue != null }.map { it.type }}",
        )
        // 비교도 불가능하다 — 기준이 될 행 자체가 없다.
        assertTrue(state.metrics.all { it.changePercent == null && it.trend == "UNKNOWN" })
        // 없는 추이를 그리지 않는다.
        assertTrue(state.metrics.all { it.history.isEmpty() }, "무데이터인데 history 가 있다")
        // 왜 비었는지 카드가 스스로 밝힌다.
        assertTrue(
            state.metrics.all { it.unavailableReason == LiveDashboardUseCase.METRIC_NOT_MEASURED_YET },
            "미측정 사유가 없다: ${state.metrics.map { it.unavailableReason }}",
        )
    }

    /**
     * **이 구분이 이번 수정의 핵심이다.** 어제·오늘 각각 0 건이 실제로 기록됐다면
     * 그 0 은 측정 결과다. 무데이터로 뭉뚱그리면 실제 관찰을 잃는다.
     */
    @Test
    @DisplayName("실제로 0인 행이 있으면 dataAvailable=true 다")
    fun actualZeroRowsAreMeasuredData() {
        val today = LocalDate.now()
        val state = stateFor(
            listOf(row(today.minusDays(1), 0), row(today, 0)),
        )

        assertEquals(true, state.dataAvailable, "측정된 0 을 무데이터로 뭉갰다")
        // 값 자체는 무데이터와 같지만 출처가 다르다는 것이 플래그로 드러난다.
        assertTrue(state.metrics.all { it.currentValue == 0L })
        assertEquals(2, state.metrics.first().history.size)
    }

    @Test
    @DisplayName("행이 하나뿐이어도 측정된 데이터다")
    fun singleRowIsMeasuredData() {
        val state = stateFor(listOf(row(LocalDate.now(), 100)))

        assertEquals(true, state.dataAvailable)
        // 비교할 앞 행이 없으므로 증감률만 비교 불가다. 데이터 부재와는 다른 사실이다.
        assertTrue(state.metrics.all { it.changePercent == null && it.trend == "UNKNOWN" })
        assertEquals(100L, state.metrics.single { it.type == "VIEWS" }.currentValue)
    }

    /**
     * `lastUpdated` 는 "마지막 갱신 시각"이다. 갱신된 적 없는 데이터에 `now()` 를 붙이면
     * 클라이언트가 "마지막 업데이트: 방금"을 그려 **비어 있는 화면이 최신처럼** 보인다.
     */
    @Test
    @DisplayName("무데이터에는 갱신 시각을 붙이지 않는다")
    fun noDataHasNoLastUpdated() {
        assertNull(stateFor(emptyList()).lastUpdated)
    }

    @Test
    @DisplayName("측정된 데이터가 있으면 갱신 시각을 제공한다")
    fun measuredDataHasLastUpdated() {
        val lastUpdated = stateFor(listOf(row(LocalDate.now(), 10))).lastUpdated

        assertTrue(!lastUpdated.isNullOrBlank(), "측정 데이터가 있는데 갱신 시각이 없다")
    }

    /**
     * 채널 연동과 데이터 수집은 다른 사실이다. 연동 직후에는 연결돼 있지만 수집된
     * 행이 없다 — 그때 `isConnected=true` 를 데이터 있음으로 읽으면 안 된다.
     */
    @Test
    @DisplayName("연동 여부와 데이터 가용성은 별개다")
    fun connectionAndDataAvailabilityAreIndependent() {
        val state = stateFor(emptyList())

        assertEquals(false, state.isConnected)
        assertEquals(false, state.dataAvailable)
    }

    /** 무데이터 응답이 JSON 에서도 플래그를 실제로 실어 보내는지 고정한다. */
    @Test
    @DisplayName("무데이터 상태는 JSON에 dataAvailable=false 로 나간다")
    fun noDataSerializesTheFlag() {
        val json = jacksonObjectMapper().writeValueAsString(stateFor(emptyList()))

        assertTrue(json.contains("\"dataAvailable\":false"), "플래그가 없다:\n$json")
        assertTrue(json.contains("\"lastUpdated\":null"), "갱신 시각이 null 이 아니다:\n$json")
    }

    // ── 비교 기준이 없을 때 ───────────────────────────────────────────────────

    /** **이 케이스가 "▲100%" 를 만들던 자리다.** */
    @Test
    @DisplayName("이전 값이 0이고 이번에 값이 생기면 100%가 아니라 비교 불가다")
    fun zeroToPositiveIsNotComparable() {
        val metric = views(previous = 0, current = 50_000)

        assertNull(metric.changePercent, "이전 값이 0인데 증감률을 계산했다")
        assertEquals("UNKNOWN", metric.trend)
        // 측정값 자체는 그대로 전달한다. 비교만 불가능한 것이다.
        assertEquals(50_000L, metric.currentValue)
        assertEquals(0L, metric.previousValue)
    }

    @Test
    @DisplayName("둘 다 0이면 변화 없음이 아니라 비교 불가다")
    fun zeroToZeroIsNotComparable() {
        val metric = views(previous = 0, current = 0)

        assertNull(metric.changePercent, "0.0 은 '변화 없음'으로 읽혀 비교 불가와 구분되지 않는다")
        assertEquals("UNKNOWN", metric.trend, "STABLE 은 측정 결과를 주장한다")
    }

    /** 정책이 VIEWS 한 지표에만 붙으면 나머지 5개가 계속 거짓을 말한다. */
    @Test
    @DisplayName("6개 지표 전부 같은 비교 불가 정책을 따른다")
    fun allMetricsShareThePolicy() {
        val metrics = metricsFor(previous = 0, current = 100)

        assertEquals(6, metrics.size)
        assertTrue(
            metrics.all { it.changePercent == null && it.trend == "UNKNOWN" },
            "비교 불가가 아닌 지표가 있다: ${metrics.filter { it.changePercent != null }.map { it.type }}",
        )
    }

    // ── 측정된 값은 그대로 ───────────────────────────────────────────────────

    @Test
    @DisplayName("이전 값이 있으면 실제 증감률과 방향을 그대로 유지한다")
    fun measuredIncreaseKeepsValueAndTrend() {
        val metric = views(previous = 100, current = 150)

        assertEquals(50.0, metric.changePercent)
        assertEquals("UP", metric.trend)
    }

    @Test
    @DisplayName("감소도 실제 값과 DOWN 방향을 유지한다")
    fun measuredDecreaseKeepsValueAndTrend() {
        val metric = views(previous = 200, current = 150)

        assertEquals(-25.0, metric.changePercent)
        assertEquals("DOWN", metric.trend)
    }

    /**
     * **측정된 0% 는 사실이다.** 어제도 오늘도 500 이면 "변화 없음"이 맞다.
     * 비교 불가와 섞으면 실제 관찰을 잃는다 — 과도한 null 처리 회귀를 여기서 막는다.
     */
    @Test
    @DisplayName("0이 아닌 같은 값은 0%와 STABLE로 남는다")
    fun genuineNoChangeStaysStable() {
        val metric = views(previous = 500, current = 500)

        assertEquals(0.0, metric.changePercent)
        assertEquals("STABLE", metric.trend)
    }

    /** 1% 이내 변동은 예전부터 STABLE 이었다. 그 임계값을 바꾸지 않았다. */
    @Test
    @DisplayName("1% 이내 미세 변동은 기존대로 STABLE이다")
    fun smallChangeStaysStable() {
        val metric = views(previous = 1000, current = 1005)

        assertEquals(0.5, metric.changePercent)
        assertEquals("STABLE", metric.trend)
    }

    // ── API 계약 ─────────────────────────────────────────────────────────────

    /**
     * 프론트 소비자가 없으므로 **직렬화 형태를 여기서 고정한다.**
     * `changePercent` 가 JSON 에서 `0` 이나 `100` 으로 나가면 클라이언트는 비교 불가를
     * 구분할 수 없다. `null` 그대로여야 한다.
     */
    @Test
    @DisplayName("비교 불가 지표는 JSON에서 changePercent가 null로 나간다")
    fun unavailableChangeSerializesAsNull() {
        val metric = views(previous = 0, current = 100)
        val json = jacksonObjectMapper().writeValueAsString(metric)

        assertTrue(json.contains("\"changePercent\":null"), "changePercent 가 null 이 아니다:\n$json")
        assertTrue(json.contains("\"trend\":\"UNKNOWN\""), "trend 가 UNKNOWN 이 아니다:\n$json")
        assertTrue(!json.contains("\"changePercent\":0"), "비교 불가가 0 으로 나갔다:\n$json")
        assertTrue(!json.contains("\"changePercent\":100"), "비교 불가가 100 으로 나갔다:\n$json")
    }

    @Test
    @DisplayName("측정된 지표는 JSON에서 숫자로 나간다")
    fun measuredChangeSerializesAsNumber() {
        val metric = views(previous = 100, current = 150)
        val json = jacksonObjectMapper().writeValueAsString(metric)

        assertTrue(json.contains("\"changePercent\":50.0"), "측정값이 훼손됐다:\n$json")
        assertTrue(json.contains("\"trend\":\"UP\""), "방향이 훼손됐다:\n$json")
    }

    /**
     * `UNKNOWN` 은 기존 `UP`/`DOWN`/`STABLE` 계약에 **추가**한 네 번째 값이다.
     * 기존 세 값의 의미는 하나도 바뀌지 않았다는 것을 함께 고정한다.
     */
    @Test
    @DisplayName("trend 는 네 가지 값만 낸다")
    fun trendVocabularyIsClosed() {
        val allowed = setOf("UP", "DOWN", "STABLE", LiveDashboardUseCase.TREND_UNKNOWN)
        val observed = listOf(
            views(previous = 0, current = 100),
            views(previous = 0, current = 0),
            views(previous = 100, current = 150),
            views(previous = 200, current = 150),
            views(previous = 500, current = 500),
        ).map { it.trend }

        assertTrue(observed.all { it in allowed }, "알 수 없는 trend 값: ${observed - allowed}")
        assertEquals(setOf("UNKNOWN", "UP", "DOWN", "STABLE"), observed.toSet())
    }
}
