package com.ongo.infrastructure.external

import com.ongo.infrastructure.external.youtube.GoogleOAuthApi
import com.ongo.infrastructure.external.youtube.YouTubeAnalyticsApi
import com.ongo.infrastructure.external.youtube.YouTubeApi
import com.ongo.infrastructure.external.youtube.YouTubeClient
import com.ongo.infrastructure.external.youtube.YouTubeConfig
import com.ongo.infrastructure.external.youtube.dto.YouTubeChannelListResponse
import com.ongo.infrastructure.external.youtube.dto.YouTubePlaylistItemListResponse
import com.ongo.infrastructure.external.youtube.dto.YouTubeVideoListResponse
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `listVideos` 가 **재지 못한 피드 지표를 0 으로 만들지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * viewCount = stats?.viewCount?.toLongOrNull() ?: 0
 * ```
 *
 * YouTube 는 비공개·통계 숨김 영상의 `statistics` 를 주지 않는다. `?: 0` 이 그 자리를
 * 채워 목록 화면이 **"조회수 0"** 을 측정 결과처럼 그렸고, 조회수 정렬에서 그 영상이
 * 맨 아래로 밀렸다.
 *
 * 이 값은 저장되지 않고 `/videos/feed` 응답으로 바로 나가므로 `null` 을 그대로 실어
 * 보낼 수 있다 — `analytics_daily`(`NOT NULL DEFAULT 0`) 와 다른 점이다.
 *
 * ## 왜 응답 DTO 를 직접 넣는가
 *
 * 외부 API 를 흉내 내 **없는 수치를 지어내지 않는다.** 검증 대상은 "응답에 값이 없을 때
 * 무엇이 되는가" 라는 매핑 계약이므로, 값이 빠진 응답과 실제로 `"0"` 을 준 응답을 구분해
 * 넣고 결과가 달라야 한다는 것만 본다.
 */
class VideoFeedMappingContractTest {

    private fun youTubeClient(statistics: YouTubeVideoListResponse.Statistics?): YouTubeClient {
        val api = mockk<YouTubeApi>()
        every { api.listChannels(any(), any(), any()) } returns YouTubeChannelListResponse(
            items = listOf(
                YouTubeChannelListResponse.YouTubeChannelItem(
                    id = "UC_me",
                    snippet = null,
                    statistics = null,
                    contentDetails = YouTubeChannelListResponse.ChannelContentDetails(
                        relatedPlaylists = mapOf("uploads" to "UU_me"),
                    ),
                ),
            ),
        )
        every { api.listPlaylistItems(any(), any(), any(), any(), any()) } returns
            YouTubePlaylistItemListResponse(
                items = listOf(
                    YouTubePlaylistItemListResponse.PlaylistItem(
                        snippet = null,
                        contentDetails = YouTubePlaylistItemListResponse.PlaylistContentDetails(
                            videoId = "v1",
                        ),
                    ),
                ),
            )
        every { api.listVideos(any(), any(), any()) } returns YouTubeVideoListResponse(
            items = listOf(
                YouTubeVideoListResponse.YouTubeVideoItem(
                    id = "v1",
                    snippet = null,
                    status = null,
                    statistics = statistics,
                ),
            ),
        )
        return YouTubeClient(
            api,
            mockk<YouTubeAnalyticsApi>(relaxed = true),
            mockk<GoogleOAuthApi>(relaxed = true),
            mockk<YouTubeConfig>(relaxed = true),
        )
    }

    private fun feedItem(statistics: YouTubeVideoListResponse.Statistics?) =
        youTubeClient(statistics).listVideos("token", null, 20, null).items.single()

    private fun statistics(viewCount: String?) = YouTubeVideoListResponse.Statistics(
        viewCount = viewCount,
        likeCount = "10",
        commentCount = "5",
    )

    /** **이 케이스가 "조회수 0" 을 측정 결과처럼 만들던 자리다.** */
    @Test
    @DisplayName("statistics 가 없으면 피드 지표는 null 이다")
    fun missingStatisticsBecomesNull() {
        val item = feedItem(statistics = null)

        assertNull(item.viewCount, "재지 못한 조회수를 0 으로 냈다")
        assertNull(item.likeCount)
        assertNull(item.commentCount)
    }

    /** 통계를 숨긴 영상은 개별 필드가 빠진다. */
    @Test
    @DisplayName("조회수 필드가 없으면 null 이고 나머지는 남는다")
    fun missingViewCountBecomesNull() {
        val item = feedItem(statistics(viewCount = null))

        assertNull(item.viewCount, "재지 못한 조회수를 0 으로 냈다")
        assertEquals(10L, item.likeCount, "측정된 값까지 잃었다")
        assertEquals(5L, item.commentCount)
    }

    /** **응답이 명시한 "0" 은 관측이다.** */
    @Test
    @DisplayName("응답이 0 을 주면 0 으로 보존한다")
    fun explicitZeroIsPreserved() {
        val item = feedItem(
            YouTubeVideoListResponse.Statistics(viewCount = "0", likeCount = "0", commentCount = "0"),
        )

        assertEquals(0L, item.viewCount, "실측 0 을 미측정으로 감췄다")
        assertEquals(0L, item.likeCount)
        assertEquals(0L, item.commentCount)
    }

    @Test
    @DisplayName("측정된 지표는 그대로 낸다")
    fun measuredCountsPassThrough() {
        val item = feedItem(statistics(viewCount = "1000"))

        assertEquals(1_000L, item.viewCount)
        assertEquals(10L, item.likeCount)
        assertEquals(5L, item.commentCount)
    }

    /**
     * **공유 수는 이 API 가 주지 않는다.**
     *
     * `videos.list` 의 `statistics` 에는 공유 수가 없다. 0 으로 채우면 "공유 0회" 라는
     * 관측이 되므로 기본값 `null` 로 남긴다.
     */
    @Test
    @DisplayName("YouTube 가 주지 않는 공유 수는 null 이다")
    fun unsupportedShareCountStaysNull() {
        assertNull(feedItem(statistics(viewCount = "1000")).shareCount)
    }
}
