package com.ongo.application.publicapi

import com.ongo.application.analytics.AnalyticsUseCase
import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.publicapi.PublicApiPostRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 공개 API 분석 응답이 **미수집 지표를 0 으로 위장하지 않는지** 고정한다.
 *
 * ## 왜 `0` 이 아니라 지표 생략인가
 *
 * 이 응답은 외부 연동이 읽는다. `total = "0"` 을 주면 소비자는 그것을 **측정된 0** 으로
 * 읽고 차트를 그린다. `available` 같은 새 필드를 더해도 그 필드를 모르는 기존 소비자는
 * 여전히 0 을 그리므로 위장이 남는다. 목록에서 빼면 **모르는 소비자도 자연히 아무것도
 * 그리지 않는다.**
 *
 * ## 무엇을 거르나
 *
 * `PinterestClient.kt:158` 의 `likes` 는 `SAVE`(저장), `:160` 의 `shares` 는
 * `PIN_CLICK`(클릭)이다. 하드코딩 0 과 달리 **다른 뜻의 큰 숫자**라 그대로 나가면
 * 외부 대시보드에 그럴듯한 성과로 뜬다.
 */
class PublicApiAnalyticsMeasurementTest {

    private val postRepository = mockk<PublicApiPostRepository>()
    private val analyticsUseCase = mockk<AnalyticsUseCase>()
    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()

    private val useCase = PublicApiAnalyticsUseCase(
        postRepository = postRepository,
        analyticsUseCase = analyticsUseCase,
        analyticsRepository = analyticsRepository,
        videoUploadRepository = videoUploadRepository,
    )

    private val userId = 7L
    private val channelId = 3L

    /** 한 채널은 한 플랫폼에 속한다. 그 채널의 업로드 하나와 집계 한 줄을 만든다. */
    private fun givenChannel(platform: Platform, views: Int, likes: Int, comments: Int, shares: Int) {
        every { videoUploadRepository.findByUserId(userId) } returns listOf(
            VideoUpload(id = 101L, videoId = 11L, platform = platform, channelId = channelId),
        )
        every { analyticsRepository.findByVideoUploadIdsAndDateRange(any(), any(), any()) } returns mapOf(
            101L to listOf(
                AnalyticsDaily(
                    videoUploadId = 101L,
                    date = LocalDate.now(),
                    views = views,
                    likes = likes,
                    commentsCount = comments,
                    shares = shares,
                ),
            ),
        )
    }

    private fun labels() = useCase.platform(userId, channelId.toString(), 30).map { it.label }

    private fun totalOf(label: String) = useCase.platform(userId, channelId.toString(), 30)
        .single { it.label == label }
        .data
        .single()
        .total

    // ── 미수집 지표는 목록에서 빠진다 ────────────────────────────────────────

    /** **이 케이스가 저장 수를 "Likes" 로, 클릭 수를 "Shares" 로 내보내던 자리다.** */
    @Test
    @DisplayName("Pinterest 는 좋아요·댓글·공유 지표를 내보내지 않는다")
    fun pinterestOmitsUncollectedMetrics() {
        givenChannel(Platform.PINTEREST, views = 500, likes = 300, comments = 0, shares = 400)

        val labels = labels()

        assertTrue("Likes" !in labels, "저장(Save) 수가 Likes 로 나갔다")
        assertTrue("Shares" !in labels, "클릭(PIN_CLICK) 수가 Shares 로 나갔다")
        assertTrue("Comments" !in labels, "수집하지 않는 댓글이 나갔다")
        // 노출은 실제로 조회하므로 Views 는 남는다.
        assertTrue("Views" in labels)
        assertEquals("500", totalOf("Views"))
    }

    @Test
    @DisplayName("Tumblr 는 조회수 지표를 내보내지 않는다")
    fun tumblrOmitsViews() {
        givenChannel(Platform.TUMBLR, views = 900_000, likes = 60, comments = 20, shares = 20)

        val labels = labels()

        assertTrue("Views" !in labels, "노트 총합이 Views 로 나갔다")
        // Tumblr 는 노트 목록에서 좋아요·답글·리블로그를 실제로 센다.
        assertTrue("Likes" in labels)
    }

    @Test
    @DisplayName("Facebook 은 공유 지표를 내보내지 않는다")
    fun facebookOmitsShares() {
        givenChannel(Platform.FACEBOOK, views = 500, likes = 40, comments = 12, shares = 0)

        val labels = labels()

        assertTrue("Shares" !in labels, "수집하지 않는 공유 0 이 나갔다")
        assertTrue("Views" in labels)
        assertTrue("Likes" in labels)
        assertTrue("Comments" in labels)
    }

    // ── 수집 지표는 그대로 ───────────────────────────────────────────────────

    @Test
    @DisplayName("YouTube 는 네 지표를 모두 내보낸다")
    fun youtubeKeepsEveryMetric() {
        givenChannel(Platform.YOUTUBE, views = 1_000, likes = 50, comments = 10, shares = 5)

        assertEquals(listOf("Views", "Likes", "Comments", "Shares"), labels())
        assertEquals("1000", totalOf("Views"))
        assertEquals("50", totalOf("Likes"))
    }

    /** **측정된 0 은 관측이다.** 수집하는 플랫폼의 0 은 그대로 내보낸다. */
    @Test
    @DisplayName("수집 플랫폼의 실제 0은 지표로 내보낸다")
    fun measuredZeroIsStillReported() {
        givenChannel(Platform.YOUTUBE, views = 1_000, likes = 0, comments = 0, shares = 0)

        assertTrue("Likes" in labels(), "실측 0 을 미수집으로 감췄다")
        assertEquals("0", totalOf("Likes"))
    }

    /** 알 수 없는 플랫폼은 fail-closed — 어떤 지표도 내보내지 않는다. */
    @Test
    @DisplayName("분석 API 가 없는 플랫폼은 지표를 내보내지 않는다")
    fun naverClipReportsNothing() {
        givenChannel(Platform.NAVER_CLIP, views = 100, likes = 10, comments = 5, shares = 2)

        assertTrue(labels().isEmpty(), "수집하지 않는 플랫폼의 숫자가 나갔다")
    }

    @Test
    @DisplayName("채널에 업로드가 없으면 빈 목록이다")
    fun noUploadsProducesEmptyList() {
        every { videoUploadRepository.findByUserId(userId) } returns emptyList()

        assertTrue(useCase.platform(userId, channelId.toString(), 30).isEmpty())
    }
}
