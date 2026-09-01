package com.ongo.infrastructure.external

import com.ongo.common.exception.PlatformApiException
import com.ongo.infrastructure.external.dailymotion.DailymotionApi
import com.ongo.infrastructure.external.dailymotion.DailymotionClient
import com.ongo.infrastructure.external.dailymotion.DailymotionConfig
import com.ongo.infrastructure.external.dailymotion.DailymotionOAuthApi
import com.ongo.infrastructure.external.dailymotion.dto.DailymotionVideoResponse
import com.ongo.infrastructure.external.threads.ThreadsApi
import com.ongo.infrastructure.external.threads.ThreadsClient
import com.ongo.infrastructure.external.threads.ThreadsConfig
import com.ongo.infrastructure.external.threads.ThreadsOAuthApi
import com.ongo.infrastructure.external.threads.dto.ThreadsInsightsResponse
import com.ongo.infrastructure.external.youtube.GoogleOAuthApi
import com.ongo.infrastructure.external.youtube.YouTubeAnalyticsApi
import com.ongo.infrastructure.external.youtube.YouTubeApi
import com.ongo.infrastructure.external.youtube.YouTubeClient
import com.ongo.infrastructure.external.youtube.YouTubeConfig
import com.ongo.infrastructure.external.youtube.dto.YouTubeAnalyticsResponse
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * `getVideoAnalytics` 가 **지원 지표의 응답 누락을 0 으로 저장하지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 클라이언트마다 `viewCount ?: 0` 이었고, 그 값을 `AnalyticsSyncScheduler` 가
 * `analytics_daily` 에 저장한다. 그 컬럼들은 전부 `NOT NULL DEFAULT 0` 이라
 * **"응답에 없었다" 와 "실제로 0 이었다" 가 같은 행이 되고, 이후 어떤 계층도 두 사실을
 * 되살릴 수 없다.**
 *
 * 저장 계층이 `null` 을 담지 못하므로 정직해질 수 있는 지점은 **저장 전에 멈추는 것**뿐이다.
 * [PlatformApiException] 을 던지면 스케줄러가 그 날짜를 쓰지 않고, 그 날짜는
 * `existingDates` 에 남지 않아 다음 실행에서 다시 시도된다.
 *
 * ## 검증하지 않는 것
 *
 * **플랫폼이 아예 주지 않는 지표는 대상이 아니다.** 시청 시간·구독 증가처럼 클라이언트가
 * `0` 을 하드코딩하는 자리는 `PlatformMetricAvailability` 가 미수집으로 선언해 소비
 * 단계에서 숨긴다. 거기에 예외를 던지면 정상 응답이 매번 실패한다.
 *
 * ## 왜 응답 DTO 를 직접 넣는가
 *
 * 외부 API 를 흉내 내 **없는 수치를 지어내지 않는다.** 검증 대상은 "응답에 값이 없을 때
 * 무엇이 되는가" 라는 매핑 계약이므로, 값이 빠진 응답과 실제로 `0` 을 준 응답을 구분해
 * 넣고 **결과가 달라야 한다**는 것만 본다.
 */
class VideoAnalyticsMissingMetricContractTest {

    private val today = LocalDate.of(2026, 8, 1)

    // ── nullable DTO 계열 (Dailymotion) ──────────────────────────────────────

    private fun dailymotionClient(
        viewsTotal: Long?,
        likesTotal: Long? = 10,
        commentsTotal: Long? = 5,
    ): DailymotionClient {
        val api = mockk<DailymotionApi>()
        every { api.getVideoLegacy(any(), any(), any()) } returns DailymotionVideoResponse(
            title = "영상",
            url = "https://dailymotion.com/video/x1",
            status = "published",
            viewsTotal = viewsTotal,
            likesTotal = likesTotal,
            commentsTotal = commentsTotal,
            bookmarksTotal = 3,
            duration = 60,
        )
        return DailymotionClient(
            api,
            mockk<DailymotionOAuthApi>(relaxed = true),
            mockk<DailymotionConfig>(relaxed = true),
            com.fasterxml.jackson.databind.ObjectMapper(),
            mockk<com.ongo.infrastructure.external.platform.PlatformFileTransferHelper>(relaxed = true),
        )
    }

    private fun dailymotionAnalytics(client: DailymotionClient) =
        client.getVideoAnalytics("x1", "token", today, today)

    /** **이 케이스가 `?: 0` 으로 미수집을 실측 0 처럼 저장하던 자리다.** */
    @Test
    @DisplayName("Dailymotion 응답에 조회수가 없으면 저장하지 않고 실패한다")
    fun dailymotionMissingViewsFailsClosed() {
        val client = dailymotionClient(viewsTotal = null)

        val error = assertThrows<PlatformApiException> { dailymotionAnalytics(client) }

        // 어떤 지표가 빠졌는지 메시지에 남아야 스케줄러 경고 로그로 원인을 알 수 있다.
        assert("views_total" in error.message) { "누락 지표를 알리지 않았다: ${error.message}" }
    }

    @Test
    @DisplayName("Dailymotion 응답에 좋아요·댓글이 없어도 실패한다")
    fun dailymotionMissingEngagementFailsClosed() {
        assertThrows<PlatformApiException> {
            dailymotionAnalytics(dailymotionClient(viewsTotal = 100, likesTotal = null))
        }
        assertThrows<PlatformApiException> {
            dailymotionAnalytics(dailymotionClient(viewsTotal = 100, commentsTotal = null))
        }
    }

    /** **응답이 명시한 0 은 관측이다.** 반드시 그대로 저장돼야 한다. */
    @Test
    @DisplayName("Dailymotion 이 명시한 0 은 0 으로 보존한다")
    fun dailymotionExplicitZeroIsPreserved() {
        val analytics = dailymotionAnalytics(
            dailymotionClient(viewsTotal = 0, likesTotal = 0, commentsTotal = 0),
        )

        assertEquals(0L, analytics.views, "실측 0 을 실패로 바꿨다")
        assertEquals(0L, analytics.likes)
        assertEquals(0L, analytics.comments)
    }

    /** 기존 정상 응답은 그대로 통과한다 — 과도한 차단 회귀를 막는다. */
    @Test
    @DisplayName("Dailymotion 정상 응답은 그대로 통과한다")
    fun dailymotionNormalResponsePassesThrough() {
        val analytics = dailymotionAnalytics(dailymotionClient(viewsTotal = 1_000))

        assertEquals(1_000L, analytics.views)
        assertEquals(10L, analytics.likes)
        assertEquals(5L, analytics.comments)
        // 미지원 지표는 예전처럼 0 이다 — availability 가 소비 단계에서 숨긴다.
        assertEquals(0L, analytics.watchTimeSeconds)
        assertEquals(0, analytics.subscriberGained)
    }

    // ── 엔트리 누산 계열 (Threads) ───────────────────────────────────────────

    private fun threadsClient(entries: List<Pair<String, Long?>>): ThreadsClient {
        val api = mockk<ThreadsApi>()
        every { api.getInsights(any(), any(), any()) } returns ThreadsInsightsResponse(
            data = entries.map { (name, value) ->
                ThreadsInsightsResponse.InsightEntry(
                    name = name,
                    values = value?.let { listOf(ThreadsInsightsResponse.InsightValue(it)) },
                )
            },
        )
        return ThreadsClient(api, mockk<ThreadsOAuthApi>(relaxed = true), mockk<ThreadsConfig>(relaxed = true))
    }

    private val threadsAll = listOf<Pair<String, Long?>>(
        "views" to 100, "likes" to 10, "replies" to 5, "reposts" to 2,
    )

    /** **`var views = 0L` 로 시작하면 엔트리가 없어도 0 이 나온다.** */
    @Test
    @DisplayName("Threads 응답에 지표 엔트리가 없으면 실패한다")
    fun threadsMissingEntryFailsClosed() {
        val client = threadsClient(threadsAll.filterNot { it.first == "views" })

        val error = assertThrows<PlatformApiException> {
            client.getVideoAnalytics("t1", "token", today, today)
        }

        assert("views" in error.message) { "누락 지표를 알리지 않았다: ${error.message}" }
    }

    /** 엔트리는 왔는데 값이 비어 있는 경우도 잰 적이 없다. */
    @Test
    @DisplayName("Threads 엔트리에 값이 없으면 실패한다")
    fun threadsEntryWithoutValueFailsClosed() {
        val client = threadsClient(threadsAll.map { if (it.first == "likes") "likes" to null else it })

        assertThrows<PlatformApiException> { client.getVideoAnalytics("t1", "token", today, today) }
    }

    /** **엔트리가 0 을 주면 그 0 은 관측이다.** */
    @Test
    @DisplayName("Threads 가 명시한 0 은 0 으로 보존한다")
    fun threadsExplicitZeroIsPreserved() {
        val client = threadsClient(threadsAll.map { it.first to 0L })

        val analytics = client.getVideoAnalytics("t1", "token", today, today)

        assertEquals(0L, analytics.views, "실측 0 을 실패로 바꿨다")
        assertEquals(0L, analytics.likes)
        assertEquals(0L, analytics.comments)
        assertEquals(0L, analytics.shares)
    }

    @Test
    @DisplayName("Threads 정상 응답은 그대로 통과한다")
    fun threadsNormalResponsePassesThrough() {
        val analytics = threadsClient(threadsAll).getVideoAnalytics("t1", "token", today, today)

        assertEquals(100L, analytics.views)
        assertEquals(10L, analytics.likes)
        assertEquals(5L, analytics.comments)
        assertEquals(2L, analytics.shares)
    }

    // ── 문자열 행 파싱 계열 (YouTube) ────────────────────────────────────────

    private fun youTubeClient(row: List<String>): YouTubeClient {
        val analyticsApi = mockk<YouTubeAnalyticsApi>()
        every {
            analyticsApi.queryAnalytics(any(), any(), any(), any(), any(), any())
        } returns YouTubeAnalyticsResponse(rows = listOf(row))
        return YouTubeClient(
            mockk<YouTubeApi>(relaxed = true),
            analyticsApi,
            mockk<GoogleOAuthApi>(relaxed = true),
            mockk<YouTubeConfig>(relaxed = true),
        )
    }

    private val youTubeRow = listOf("100", "10", "5", "2", "60", "3", "500", "45")

    /** YouTube 는 여덟 지표를 모두 조회한다 — 빈 칸은 미수집이 아니라 응답 이상이다. */
    @Test
    @DisplayName("YouTube 행의 값이 숫자가 아니면 실패한다")
    fun youTubeUnparseableCellFailsClosed() {
        val client = youTubeClient(youTubeRow.toMutableList().also { it[0] = "" })

        val error = assertThrows<PlatformApiException> {
            client.getVideoAnalytics("v1", "token", today, today)
        }

        assert("views" in error.message) { "누락 지표를 알리지 않았다: ${error.message}" }
    }

    /** **행이 명시한 0 은 관측이다.** */
    @Test
    @DisplayName("YouTube 행의 0 은 0 으로 보존한다")
    fun youTubeExplicitZeroIsPreserved() {
        val analytics = youTubeClient(List(8) { "0" }).getVideoAnalytics("v1", "token", today, today)

        assertEquals(0L, analytics.views, "실측 0 을 실패로 바꿨다")
        assertEquals(0L, analytics.likes)
        assertEquals(0L, analytics.watchTimeSeconds)
        assertEquals(0, analytics.subscriberGained)
        assertEquals(0L, analytics.impressions)
    }

    @Test
    @DisplayName("YouTube 정상 응답은 그대로 통과한다")
    fun youTubeNormalResponsePassesThrough() {
        val analytics = youTubeClient(youTubeRow).getVideoAnalytics("v1", "token", today, today)

        assertEquals(100L, analytics.views)
        assertEquals(10L, analytics.likes)
        assertEquals(5L, analytics.comments)
        assertEquals(2L, analytics.shares)
        // 분 → 초 변환은 그대로다.
        assertEquals(3_600L, analytics.watchTimeSeconds)
        assertEquals(3, analytics.subscriberGained)
        assertEquals(500L, analytics.impressions)
        assertEquals(45L, analytics.avgViewDurationSeconds)
    }
}
