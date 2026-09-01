package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channel.FeedItemResult
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.PlatformFeedPortResult
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 영상 피드가 **재지 못한 지표를 0 으로 보여주거나 줄 세우지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * `FeedItem` 의 네 지표가 `Long = 0` 이었다. Instagram 미디어 목록 API 는 **조회수를 아예
 * 주지 않는데** 그 자리가 `0` 이 되어,
 *
 * - 목록 화면(`VideosView`)이 "조회수 0" 을 측정 결과처럼 그리고,
 * - 조회수 정렬에서 **Instagram 영상이 통째로 맨 아래에 깔렸다.**
 *
 * 이 값은 어디에도 저장되지 않고 `/videos/feed` 응답으로 바로 나가므로,
 * `analytics_daily`(`NOT NULL DEFAULT 0`) 와 달리 `null` 을 그대로 실어 보낼 수 있다.
 */
class VideoFeedMeasurementTest {

    private val channelRepository = mockk<ChannelRepository>()
    private val platformClientPort = mockk<PlatformClientPort>()
    private val videoUploadRepository = mockk<VideoUploadRepository>(relaxed = true)
    private val tokenEncryptionPort = mockk<TokenEncryptionPort>(relaxed = true)

    private val useCase = VideoFeedUseCase(
        channelRepository = channelRepository,
        platformClientPort = platformClientPort,
        videoUploadRepository = videoUploadRepository,
        tokenEncryptionPort = tokenEncryptionPort,
    )

    private val userId = 7L

    private fun feedItem(id: String, viewCount: Long?) = FeedItemResult(
        platformVideoId = id,
        title = "영상 $id",
        viewCount = viewCount,
        likeCount = 10,
        commentCount = 5,
        publishedAt = "2026-08-0${id}T00:00:00Z",
    )

    private fun given(items: List<FeedItemResult>) {
        every { channelRepository.findByUserId(userId) } returns listOf(
            Channel(
                id = 1L,
                userId = userId,
                platform = Platform.YOUTUBE,
                platformChannelId = "ch",
                channelName = "내 채널",
                accessToken = EncryptedToken("token"),
            ),
        )
        every { platformClientPort.listVideos(any(), any(), any(), any(), any()) } returns
            PlatformFeedPortResult(items = items)
    }

    /**
     * 이 테스트의 관심사는 **표시·정렬**이다. 페이지는 첫 페이지(0)로 고정한다 —
     * 숫자 페이지 이동은 이제 지원하지 않으며 그 계약은 [VideoFeedPaginationTest] 가 본다.
     */
    private fun feed(sort: String) = useCase.getFeed(userId, null, 0, 20, sort).items

    // ── 표시 계약 ────────────────────────────────────────────────────────────

    /** **이 케이스가 "조회수 0" 을 측정 결과처럼 내보내던 자리다.** */
    @Test
    @DisplayName("플랫폼이 조회수를 주지 않으면 null 로 내보낸다")
    fun unmeasuredViewCountStaysNull() {
        given(listOf(feedItem("1", viewCount = null)))

        assertNull(feed("date").single().viewCount, "재지 못한 조회수를 0 으로 냈다")
    }

    /** **응답이 명시한 0 은 관측이다.** */
    @Test
    @DisplayName("측정된 0 조회수는 0 으로 보존한다")
    fun measuredZeroViewCountIsPreserved() {
        given(listOf(feedItem("1", viewCount = 0)))

        assertEquals(0L, feed("date").single().viewCount, "실측 0 을 미측정으로 감췄다")
    }

    @Test
    @DisplayName("측정된 지표는 그대로 내보낸다")
    fun measuredCountsPassThrough() {
        given(listOf(feedItem("1", viewCount = 1_000)))

        val item = feed("date").single()

        assertEquals(1_000L, item.viewCount)
        assertEquals(10L, item.likeCount)
        assertEquals(5L, item.commentCount)
    }

    // ── 정렬 계약 ────────────────────────────────────────────────────────────

    /**
     * **미측정을 0 으로 줄 세우지 않는다.**
     *
     * 0 으로 취급하면 조회수를 주지 않는 플랫폼의 영상이 "가장 성과가 낮은 영상" 자리에
     * 놓인다. 모르는 값은 실측보다 낮다고 주장하지 않되 순서는 정해야 하므로 끝에 모은다.
     */
    @Test
    @DisplayName("조회수 정렬에서 미측정은 맨 뒤로 가고 실측 0 보다 아래다")
    fun unmeasuredSortsAfterMeasuredZero() {
        given(
            listOf(
                feedItem("1", viewCount = null),
                feedItem("2", viewCount = 0),
                feedItem("3", viewCount = 500),
            ),
        )

        val order = feed("views").map { it.platformVideoId }

        assertEquals(listOf("3", "2", "1"), order, "미측정이 실측보다 위에 왔다")
    }

    /** 측정된 값끼리의 내림차순은 그대로다 — 과도한 변경 회귀를 막는다. */
    @Test
    @DisplayName("측정된 조회수는 내림차순 그대로 정렬한다")
    fun measuredValuesKeepDescendingOrder() {
        given(
            listOf(
                feedItem("1", viewCount = 100),
                feedItem("2", viewCount = 900),
                feedItem("3", viewCount = 500),
            ),
        )

        assertEquals(listOf("2", "3", "1"), feed("views").map { it.platformVideoId })
    }
}
