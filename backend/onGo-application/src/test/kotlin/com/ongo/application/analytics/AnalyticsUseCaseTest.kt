package com.ongo.application.analytics

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
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
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AnalyticsUseCaseTest {
    private val analytics = mockk<AnalyticsRepository>(relaxed = true)
    private val users = mockk<UserRepository>(relaxed = true)
    private val videos = mockk<VideoRepository>(relaxed = true)
    private val uploads = mockk<VideoUploadRepository>(relaxed = true)
    private val credits = mockk<CreditRepository>(relaxed = true)
    private val useCase = AnalyticsUseCase(analytics, users, videos, uploads, credits)

    @Test
    fun `top videos expose stored analytics aggregates`() {
        val video = Video(
            id = 11L,
            userId = 7L,
            title = "실제 영상",
            status = UploadStatus.PUBLISHED,
        )
        every { analytics.getTopVideos(7L, 30, 5) } returns listOf(video)
        every { uploads.findByVideoIds(listOf(11L)) } returns mapOf(
            11L to listOf(VideoUpload(id = 101L, videoId = 11L, platform = Platform.YOUTUBE)),
        )
        every { analytics.findByVideoUploadIdsAndDateRange(any(), any(), any()) } returns mapOf(
            101L to listOf(
                AnalyticsDaily(videoUploadId = 101L, date = java.time.LocalDate.now(), views = 120, likes = 8),
                AnalyticsDaily(videoUploadId = 101L, date = java.time.LocalDate.now().minusDays(1), views = 30, likes = 2),
            ),
        )

        val item = useCase.getTopVideos(7L, 30, 5).videos.single()

        assertEquals(150L, item.totalViews)
        assertEquals(10L, item.totalLikes)
    }

    @Test
    fun `platform comparison uses engagement aggregates`() {
        every { analytics.findCrossPlatformDetailMetrics(7L, 30) } returns listOf(
            CrossPlatformDetailRaw(
                videoId = 11L,
                videoTitle = "영상",
                thumbnailUrls = emptyList(),
                publishedAt = null,
                platform = "YOUTUBE",
                videoUploadId = 101L,
                views = 100,
                likes = 12,
                comments = 3,
                shares = 4,
                watchTimeSeconds = 0,
                revenueMicro = 0,
                impressions = 0,
                avgViewDurationSeconds = 0,
            ),
        )

        val summary = useCase.getPlatformComparison(7L, 30).platforms.single()

        assertEquals(100L, summary.views)
        assertEquals(12L, summary.likes)
        assertEquals(3L, summary.comments)
        assertEquals(4L, summary.shares)
    }
}
