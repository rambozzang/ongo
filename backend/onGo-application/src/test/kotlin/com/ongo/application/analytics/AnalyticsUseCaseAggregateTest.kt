package com.ongo.application.analytics

import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.CrossPlatformDetailRaw
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.user.UserRepository
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
import kotlin.test.assertTrue

/**
 * 화면에 나가는 집계가 실제 저장값에서 나오는지 고정한다.
 *
 * 예전에는 인기 영상의 조회수/좋아요가 0 으로 하드코딩돼 있었고("populated from
 * aggregate query" 주석만 남아 있었다), 플랫폼 비교는 조회수만 채우고 좋아요·댓글·
 * 공유를 0 으로 내보냈다. 둘 다 값이 없는 게 아니라 **있는데 안 읽은** 경우였다.
 */
class AnalyticsUseCaseAggregateTest {

    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val useCase = AnalyticsUseCase(
        analyticsRepository = analyticsRepository,
        userRepository = mockk<UserRepository>(relaxed = true),
        videoRepository = mockk<VideoRepository>(relaxed = true),
        videoUploadRepository = videoUploadRepository,
        creditRepository = mockk<CreditRepository>(relaxed = true),
    )

    private fun video(id: Long, title: String) = Video(id = id, userId = 7L, title = title)

    private fun upload(id: Long, videoId: Long, platform: Platform) =
        VideoUpload(id = id, videoId = videoId, platform = platform)

    private fun daily(uploadId: Long, views: Int, likes: Int) = AnalyticsDaily(
        videoUploadId = uploadId,
        date = LocalDate.of(2026, 8, 10),
        views = views,
        likes = likes,
    )

    private fun detail(platform: String, views: Long, likes: Long, comments: Long, shares: Long) =
        CrossPlatformDetailRaw(
            videoId = 1L,
            videoTitle = "영상",
            thumbnailUrls = emptyList(),
            publishedAt = null,
            platform = platform,
            videoUploadId = 11L,
            views = views,
            likes = likes,
            comments = comments,
            shares = shares,
            watchTimeSeconds = 0,
            revenueMicro = 0,
            impressions = 0,
            avgViewDurationSeconds = 0,
        )

    @Test
    @DisplayName("인기 영상의 조회수와 좋아요를 실제 집계에서 채운다")
    fun topVideosUseRealAggregates() {
        every { analyticsRepository.getTopVideos(7L, 30, 5) } returns listOf(video(1L, "첫 영상"))
        every { videoUploadRepository.findByVideoIds(listOf(1L)) } returns mapOf(
            1L to listOf(upload(11L, 1L, Platform.YOUTUBE), upload(12L, 1L, Platform.TIKTOK)),
        )
        every { analyticsRepository.findByVideoUploadIdsAndDateRange(listOf(11L, 12L), any(), any()) } returns mapOf(
            11L to listOf(daily(11L, views = 100, likes = 7), daily(11L, views = 50, likes = 3)),
            12L to listOf(daily(12L, views = 20, likes = 1)),
        )

        val result = useCase.getTopVideos(7L, 30, 5)

        val item = result.videos.single()
        // 업로드 2건 × 일자별 행을 모두 합산한다.
        assertEquals(170L, item.totalViews)
        assertEquals(11L, item.totalLikes)
        assertEquals(listOf("YOUTUBE", "TIKTOK"), item.platforms)
    }

    @Test
    @DisplayName("집계 행이 없으면 0 으로 두되 영상 자체는 그대로 보여준다")
    fun topVideosKeepEmptyStateSemantics() {
        every { analyticsRepository.getTopVideos(7L, 7, 3) } returns listOf(video(2L, "지표 없는 영상"))
        every { videoUploadRepository.findByVideoIds(listOf(2L)) } returns mapOf(
            2L to listOf(upload(21L, 2L, Platform.YOUTUBE)),
        )
        every { analyticsRepository.findByVideoUploadIdsAndDateRange(listOf(21L), any(), any()) } returns emptyMap()

        val result = useCase.getTopVideos(7L, 7, 3)

        val item = result.videos.single()
        assertEquals("지표 없는 영상", item.title)
        assertEquals(0L, item.totalViews)
        assertEquals(0L, item.totalLikes)
    }

    @Test
    @DisplayName("플랫폼 비교가 좋아요·댓글·공유를 실제 값으로 채운다")
    fun platformComparisonUsesRealEngagement() {
        every { analyticsRepository.findCrossPlatformDetailMetrics(7L, 30) } returns listOf(
            detail("YOUTUBE", views = 100, likes = 10, comments = 5, shares = 2),
            detail("YOUTUBE", views = 50, likes = 4, comments = 1, shares = 1),
            detail("TIKTOK", views = 300, likes = 30, comments = 9, shares = 8),
        )

        val result = useCase.getPlatformComparison(7L, 30)

        val youtube = result.platforms.single { it.platform == Platform.YOUTUBE }
        assertEquals(150L, youtube.views)
        assertEquals(14L, youtube.likes)
        assertEquals(6L, youtube.comments)
        assertEquals(3L, youtube.shares)

        val tiktok = result.platforms.single { it.platform == Platform.TIKTOK }
        assertEquals(30L, tiktok.likes)
    }

    @Test
    @DisplayName("집계가 비면 빈 목록을 돌려준다")
    fun platformComparisonEmptyState() {
        every { analyticsRepository.findCrossPlatformDetailMetrics(7L, 30) } returns emptyList()

        assertTrue(useCase.getPlatformComparison(7L, 30).platforms.isEmpty())
    }

    @Test
    @DisplayName("현재 enum 에 없는 플랫폼 문자열은 0 으로 채우지 않고 건너뛴다")
    fun unknownPlatformIsSkippedNotZeroFilled() {
        every { analyticsRepository.findCrossPlatformDetailMetrics(7L, 30) } returns listOf(
            detail("YOUTUBE", views = 10, likes = 1, comments = 1, shares = 1),
            detail("MYSPACE", views = 99, likes = 9, comments = 9, shares = 9),
        )

        val result = useCase.getPlatformComparison(7L, 30)

        assertEquals(1, result.platforms.size)
        assertEquals(Platform.YOUTUBE, result.platforms.single().platform)
    }
}
