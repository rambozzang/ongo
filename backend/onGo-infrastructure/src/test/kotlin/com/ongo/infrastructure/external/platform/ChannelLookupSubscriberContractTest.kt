package com.ongo.infrastructure.external.platform

import com.ongo.infrastructure.external.youtube.YouTubeApi
import com.ongo.infrastructure.external.youtube.YouTubeConfig
import com.ongo.infrastructure.external.youtube.dto.YouTubeChannelListResponse
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 경쟁자 추가 검색이 **구독자 수를 재지 못한 자리에 0 을 넣지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * subscriberCount = item.statistics?.subscriberCount?.toLongOrNull() ?: 0
 * ```
 *
 * YouTube 채널은 구독자 수를 **숨길 수 있고**(`hiddenSubscriberCount`), 그때 응답의
 * `statistics.subscriberCount` 가 빠진다. `?: 0` 이 그 자리를 채워 **경쟁자 추가
 * 미리보기에 "구독자 0명"** 이 뜨고, 사용자가 그대로 추가하면 그 0 이 저장돼
 * 비교표·순위·평균에 관측값처럼 섞였다.
 *
 * ## 왜 응답 DTO 를 직접 넣는가
 *
 * 외부 API 를 흉내 내 **없는 수치를 지어내지 않는다.** 검증 대상은 "응답에 그 필드가
 * 없을 때 무엇이 되는가" 라는 매핑 계약이므로, 필드가 빠진 응답과 실제로 `"0"` 을 준
 * 응답을 구분해 넣는다.
 */
class ChannelLookupSubscriberContractTest {

    private fun lookup(statistics: YouTubeChannelListResponse.ChannelStatistics?): com.ongo.domain.competitor.ChannelLookupResult {
        val api = mockk<YouTubeApi>()
        every { api.searchChannelByHandle(any(), any(), any()) } returns YouTubeChannelListResponse(
            items = listOf(
                YouTubeChannelListResponse.YouTubeChannelItem(
                    id = "UC_test_channel_id_00000",
                    snippet = null,
                    statistics = statistics,
                ),
            ),
        )
        val config = mockk<YouTubeConfig>()
        every { config.getApiKey() } returns "configured-key"

        return ChannelLookupPortAdapter(api, config).lookupChannel("YOUTUBE", "@creator")
    }

    private fun statistics(subscriberCount: String?) = YouTubeChannelListResponse.ChannelStatistics(
        subscriberCount = subscriberCount,
        viewCount = "1000",
        videoCount = "12",
    )

    // ── 재지 못한 경우 ───────────────────────────────────────────────────────

    /** **이 케이스가 미리보기에 "구독자 0명" 을 그리던 자리다.** */
    @Test
    @DisplayName("구독자 수를 숨긴 채널은 null 로 응답한다")
    fun hiddenSubscriberCountBecomesNull() {
        val result = lookup(statistics(subscriberCount = null))

        assertTrue(result.found, "채널 자체는 찾았다")
        assertNull(result.subscriberCount, "숨긴 구독자 수를 0 으로 냈다")
    }

    /** `statistics` 를 통째로 주지 않는 응답. */
    @Test
    @DisplayName("statistics 가 없으면 구독자 수는 null 이다")
    fun missingStatisticsBecomesNull() {
        val result = lookup(statistics = null)

        assertTrue(result.found)
        assertNull(result.subscriberCount, "응답에 없는 값을 0 으로 냈다")
    }

    /**
     * **채널을 찾은 것과 구독자 수를 못 잰 것은 다른 사실이다.**
     *
     * 구독자 수가 없다고 `found = false` 로 만들면 사용자는 채널을 아예 추가할 수 없다.
     * 이름·영상 수 같은 나머지 값은 그대로 쓴다.
     */
    @Test
    @DisplayName("구독자 수를 못 재도 나머지 조회 결과는 그대로 쓴다")
    fun otherFieldsSurviveMissingSubscriberCount() {
        val result = lookup(statistics(subscriberCount = null))

        assertEquals("UC_test_channel_id_00000", result.platformChannelId)
        assertEquals(1_000L, result.totalViews)
        assertEquals(12, result.videoCount)
    }

    // ── 실측 보존 ────────────────────────────────────────────────────────────

    /** **응답이 실제로 "0" 을 주면 그것은 관측이다.** */
    @Test
    @DisplayName("응답이 0 을 주면 0 으로 남긴다")
    fun measuredZeroIsPreserved() {
        val result = lookup(statistics(subscriberCount = "0"))

        assertEquals(0L, result.subscriberCount, "실측 0 을 미측정으로 감췄다")
    }

    @Test
    @DisplayName("응답이 준 구독자 수는 그대로 낸다")
    fun measuredCountIsPreserved() {
        val result = lookup(statistics(subscriberCount = "8000"))

        assertEquals(8_000L, result.subscriberCount)
    }

    // ── 영상 수 ──────────────────────────────────────────────────────────────
    //
    // 영상 수는 **평균 조회수의 분모**다. 재지 못한 자리를 0 으로 채우면 "영상 0개" 라는
    // 관측이 되고, 그 0 때문에 평균까지 "계산할 수 없음" 으로 바뀐다.

    private fun videoStatistics(videoCount: String?) =
        YouTubeChannelListResponse.ChannelStatistics(
            subscriberCount = "8000",
            viewCount = "1000",
            videoCount = videoCount,
        )

    @Test
    @DisplayName("statistics 가 없으면 영상 수는 null 이다")
    fun missingStatisticsMakesVideoCountNull() {
        assertNull(lookup(statistics = null).videoCount, "응답에 없는 값을 0 으로 냈다")
    }

    /** **이 케이스가 `?: 0` 으로 없는 영상 수를 관측처럼 만들던 자리다.** */
    @Test
    @DisplayName("응답에 영상 수 필드가 없으면 null 이다")
    fun missingVideoCountBecomesNull() {
        assertNull(lookup(videoStatistics(null)).videoCount, "응답에 없는 값을 0 으로 냈다")
    }

    /** **응답이 실제로 "0" 을 주면 그것은 관측이다.** */
    @Test
    @DisplayName("응답이 영상 수 0 을 주면 0 으로 남긴다")
    fun measuredZeroVideoCountIsPreserved() {
        assertEquals(0, lookup(videoStatistics("0")).videoCount, "실측 0 을 미측정으로 감췄다")
    }

    @Test
    @DisplayName("응답이 준 영상 수는 그대로 낸다")
    fun measuredVideoCountIsPreserved() {
        assertEquals(12, lookup(videoStatistics("12")).videoCount)
    }

    // ── 총 조회수 ────────────────────────────────────────────────────────────
    //
    // 총 조회수는 **평균 조회수의 분자**다. 재지 못한 자리를 0 으로 채우면 영상 수가 있는
    // 채널에서 `0 / n = 0` 이 계산돼 "평균 0회" 라는 관측이 만들어진다.

    private fun viewStatistics(viewCount: String?) =
        YouTubeChannelListResponse.ChannelStatistics(
            subscriberCount = "8000",
            viewCount = viewCount,
            videoCount = "12",
        )

    @Test
    @DisplayName("statistics 가 없으면 총 조회수는 null 이다")
    fun missingStatisticsMakesTotalViewsNull() {
        assertNull(lookup(statistics = null).totalViews, "응답에 없는 값을 0 으로 냈다")
    }

    /** **이 케이스가 `?: 0` 으로 없는 조회수를 관측처럼 만들던 자리다.** */
    @Test
    @DisplayName("응답에 조회수 필드가 없으면 null 이다")
    fun missingViewCountBecomesNull() {
        assertNull(lookup(viewStatistics(null)).totalViews, "응답에 없는 값을 0 으로 냈다")
    }

    /** **응답이 실제로 "0" 을 주면 그것은 관측이다.** */
    @Test
    @DisplayName("응답이 조회수 0 을 주면 0 으로 남긴다")
    fun measuredZeroTotalViewsIsPreserved() {
        assertEquals(0L, lookup(viewStatistics("0")).totalViews, "실측 0 을 미측정으로 감췄다")
    }

    @Test
    @DisplayName("응답이 준 총 조회수는 그대로 낸다")
    fun measuredTotalViewsIsPreserved() {
        assertEquals(1_000L, lookup(viewStatistics("1000")).totalViews)
    }
}
