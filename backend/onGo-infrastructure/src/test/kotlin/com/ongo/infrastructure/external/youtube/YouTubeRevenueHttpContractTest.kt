package com.ongo.infrastructure.external.youtube

import com.ongo.domain.analytics.RevenueStatus
import com.ongo.infrastructure.external.platform.PlatformRestClientSupport
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.net.URLDecoder
import java.time.LocalDate

/**
 * YouTube 수익 조회의 HTTP 계약.
 *
 * ## 왜 별도 호출인가
 *
 * `estimatedRevenue` 는 `yt-analytics-monetary.readonly` 를 따로 요구한다. 기존 scope 로
 * 이미 연결된 채널은 그 권한이 없어 403 이 온다. 8개 일반 지표와 한 질의에 섞으면
 * **수익 권한 하나 때문에 조회수·좋아요·댓글까지 전부 저장되지 않는다.** 여기서 두 질의가
 * 독립임을 고정한다.
 */
class YouTubeRevenueHttpContractTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun client(): YouTubeClient {
        val restClient = PlatformRestClientSupport
            .builder(server.url("/").toString().removeSuffix("/"))
            .build()
        val analyticsApi = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(YouTubeAnalyticsApi::class.java)

        return YouTubeClient(
            youTubeApi = mockk(),
            youTubeAnalyticsApi = analyticsApi,
            googleOAuthApi = mockk(),
            youTubeConfig = mockk(),
        )
    }

    private fun json(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private val from = LocalDate.of(2026, 8, 1)
    private val to = LocalDate.of(2026, 8, 3)

    // ---- 요청 계약 ----

    @Test
    fun `수익 질의는 통화와 일별 차원을 명시한다`() {
        server.enqueue(json("""{"rows":[["2026-08-01","1500"]]}"""))

        client().getVideoRevenue("vid-1", "token", from, to)

        val query = URLDecoder.decode(server.takeRequest().path!!, "UTF-8")
        assertThat(query).contains("metrics=estimatedRevenue")
        // 통화를 지정하지 않으면 채널의 지급 통화(USD 등)로 내려와 원화로 읽을 수 없다.
        assertThat(query).contains("currency=KRW")
        // 일별 차원이 없으면 기간 전체가 한 행으로 합쳐져 하루 단위로 저장할 수 없다.
        assertThat(query).contains("dimensions=day")
        assertThat(query).contains("filters=video==vid-1")
        assertThat(query).contains("startDate=2026-08-01")
        assertThat(query).contains("endDate=2026-08-03")
        // 일반 지표는 절대 같이 요청하지 않는다.
        assertThat(query).doesNotContain("views")
    }

    /** 기존 8개 지표 질의는 그대로여야 한다 — 통화·차원이 붙으면 응답 모양이 바뀐다. */
    @Test
    fun `일반 분석 질의 계약은 바뀌지 않았다`() {
        server.enqueue(json("""{"rows":[["10","2","1","0","5","3","40","12"]]}"""))

        val analytics = client().getVideoAnalytics("vid-1", "token", from, to)

        val query = URLDecoder.decode(server.takeRequest().path!!, "UTF-8")
        assertThat(query).contains(
            "metrics=views,likes,comments,shares,estimatedMinutesWatched,subscribersGained,impressions,averageViewDuration",
        )
        assertThat(query).doesNotContain("estimatedRevenue")
        assertThat(query).doesNotContain("dimensions=")
        assertThat(query).doesNotContain("currency=")

        assertThat(analytics.views).isEqualTo(10)
        assertThat(analytics.watchTimeSeconds).isEqualTo(300) // 5분 → 초
        assertThat(analytics.avgViewDurationSeconds).isEqualTo(12)
    }

    // ---- 응답 해석 ----

    @Test
    fun `KRW 0 원과 양수를 각각 측정으로 저장한다`() {
        server.enqueue(json("""{"rows":[["2026-08-01","0"],["2026-08-02","15230.5"]]}"""))

        val report = client().getVideoRevenue("vid-1", "token", from, to)

        assertThat(report.status).isEqualTo(RevenueStatus.MEASURED)

        val zeroDay = report.daily.getValue(LocalDate.of(2026, 8, 1))
        assertThat(zeroDay.status).isEqualTo(RevenueStatus.MEASURED)
        assertThat(zeroDay.amountMicro).isEqualTo(0L)
        assertThat(zeroDay.currency).isEqualTo("KRW")

        val paidDay = report.daily.getValue(LocalDate.of(2026, 8, 2))
        assertThat(paidDay.amountMicro).isEqualTo(15_230_500_000L)

        // 응답에 없는 날짜를 0 원으로 채우지 않는다.
        assertThat(report.daily).doesNotContainKey(LocalDate.of(2026, 8, 3))
    }

    /** 행이 없다 = 확정 지연. "0 원 확정"이 아니다. */
    @Test
    fun `행이 없으면 PENDING 이다`() {
        server.enqueue(json("""{"rows":[]}"""))

        assertThat(client().getVideoRevenue("vid-1", "token", from, to).status)
            .isEqualTo(RevenueStatus.PENDING)
    }

    @Test
    fun `rows 필드 자체가 없어도 PENDING 이다`() {
        server.enqueue(json("""{}"""))

        assertThat(client().getVideoRevenue("vid-1", "token", from, to).status)
            .isEqualTo(RevenueStatus.PENDING)
    }

    @Test
    fun `금액을 읽을 수 없으면 그 날짜는 ERROR 다`() {
        server.enqueue(json("""{"rows":[["2026-08-01","N/A"]]}"""))

        val report = client().getVideoRevenue("vid-1", "token", from, to)

        assertThat(report.status).isEqualTo(RevenueStatus.MEASURED)
        val day = report.daily.getValue(LocalDate.of(2026, 8, 1))
        assertThat(day.status).isEqualTo(RevenueStatus.ERROR)
        assertThat(day.amountMicro).isNull()
    }

    @Test
    fun `날짜를 읽을 수 없는 행만 버린다`() {
        server.enqueue(json("""{"rows":[["not-a-date","100"],["2026-08-02","200"]]}"""))

        val report = client().getVideoRevenue("vid-1", "token", from, to)

        assertThat(report.daily.keys).containsExactly(LocalDate.of(2026, 8, 2))
    }

    // ---- 권한 ----

    @Test
    fun `금전 scope 가 없으면 PERMISSION_REQUIRED 다`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"code":403}}"""))

        assertThat(client().getVideoRevenue("vid-1", "token", from, to).status)
            .isEqualTo(RevenueStatus.PERMISSION_REQUIRED)
    }

    @Test
    fun `토큰이 만료되면 PERMISSION_REQUIRED 다`() {
        server.enqueue(MockResponse().setResponseCode(401))

        assertThat(client().getVideoRevenue("vid-1", "token", from, to).status)
            .isEqualTo(RevenueStatus.PERMISSION_REQUIRED)
    }

    @Test
    fun `그 밖의 실패는 ERROR 다`() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertThat(client().getVideoRevenue("vid-1", "token", from, to).status)
            .isEqualTo(RevenueStatus.ERROR)
    }

    /**
     * **이 테스트가 요구사항의 핵심이다.** 수익 403 이 같은 주기의 일반 분석 수집을
     * 무효화하면 안 된다. 두 질의가 독립이라 예외조차 서로 전달되지 않는다.
     */
    @Test
    fun `수익이 403 이어도 일반 분석 8개 지표는 그대로 조회된다`() {
        val youTube = client()

        server.enqueue(json("""{"rows":[["120","9","4","2","10","7","500","33"]]}"""))
        val analytics = youTube.getVideoAnalytics("vid-1", "token", from, to)

        server.enqueue(MockResponse().setResponseCode(403))
        val revenue = youTube.getVideoRevenue("vid-1", "token", from, to)

        assertThat(analytics.views).isEqualTo(120)
        assertThat(analytics.likes).isEqualTo(9)
        assertThat(analytics.comments).isEqualTo(4)
        assertThat(analytics.shares).isEqualTo(2)
        assertThat(analytics.watchTimeSeconds).isEqualTo(600)
        assertThat(analytics.subscriberGained).isEqualTo(7)
        assertThat(analytics.impressions).isEqualTo(500)
        assertThat(analytics.avgViewDurationSeconds).isEqualTo(33)

        assertThat(revenue.status).isEqualTo(RevenueStatus.PERMISSION_REQUIRED)
        assertThat(revenue.daily).isEmpty()
    }
}
