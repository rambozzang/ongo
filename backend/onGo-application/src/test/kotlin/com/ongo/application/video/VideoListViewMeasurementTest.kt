package com.ongo.application.video

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
import kotlin.test.assertNull

/**
 * 영상 목록의 총 조회수가 **미수집 플랫폼의 숫자를 더하지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * val totalViews = filteredUploads.sumOf { viewsByUploadId[it.id] ?: 0L }
 * ```
 *
 * `AnalyticsDaily` 에는 `videoUploadId` 만 있어 행만 봐서는 플랫폼을 알 수 없다. 그래서
 * 플랫폼 필터 없이 그대로 더했고, `TumblrClient.kt:141` 이 `views` 자리에 넣는
 * `total_notes`(노트 총합)가 목록의 "총 조회수" 로 나갔다. Naver Clip 은 분석 API 자체가
 * 없어 그 행의 숫자도 근거가 없다.
 */
class VideoListViewMeasurementTest {

    private val videoRepository = mockk<VideoRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val analyticsRepository = mockk<AnalyticsRepository>()

    private val useCase = VideoQueryUseCase(
        videoRepository = videoRepository,
        videoUploadRepository = videoUploadRepository,
        videoPlatformMetaRepository = mockk(relaxed = true),
        contentImageRepository = mockk(relaxed = true),
        storageService = mockk(relaxed = true),
        channelRepository = mockk(relaxed = true),
        tokenEncryptionPort = mockk(relaxed = true),
        platformClientPort = mockk(relaxed = true),
        analyticsRepository = analyticsRepository,
        storageQuotaUseCase = mockk(relaxed = true),
    )

    private val userId = 7L
    private val videoId = 11L

    /**
     * @param uploads (uploadId, 플랫폼, 그 업로드의 조회수 행 값)
     */
    private fun given(vararg uploads: Triple<Long, Platform, Int>) {
        every { videoRepository.findByUserId(userId, any(), any(), any()) } returns
            listOf(Video(id = videoId, userId = userId, title = "영상"))
        every { videoRepository.countByUserId(userId, any()) } returns 1

        val videoUploads = uploads.map { (id, platform, _) ->
            VideoUpload(id = id, videoId = videoId, platform = platform, channelId = 1L)
        }
        every { videoUploadRepository.findByVideoIds(listOf(videoId)) } returns mapOf(videoId to videoUploads)

        every { analyticsRepository.findByVideoUploadIds(any()) } returns uploads.map { (id, _, views) ->
            AnalyticsDaily(videoUploadId = id, date = LocalDate.now(), views = views)
        }
    }

    private fun listItem() =
        useCase.listVideos(userId, 0, 20, null, null, null).content.single()

    private fun totalViews() = listItem().totalViews

    /** **이 케이스가 노트 총합을 조회수로 내보내던 자리다.** */
    @Test
    @DisplayName("Tumblr 노트 총합을 총 조회수로 더하지 않는다")
    fun tumblrNoteCountIsNotCountedAsViews() {
        given(
            Triple(101L, Platform.YOUTUBE, 1_000),
            Triple(102L, Platform.TUMBLR, 900_000),
        )

        assertEquals(1_000L, totalViews(), "노트 총합이 조회수 합계에 섞였다")
    }

    @Test
    @DisplayName("Naver Clip 행도 총 조회수에 더하지 않는다")
    fun naverClipRowsAreNotCountedAsViews() {
        given(
            Triple(101L, Platform.YOUTUBE, 500),
            Triple(102L, Platform.NAVER_CLIP, 7_000),
        )

        assertEquals(500L, totalViews())
    }

    /** 조회수를 물어볼 수 있는 업로드가 하나도 없으면 합계 자체가 없다. */
    @Test
    @DisplayName("조회수를 수집하는 업로드가 없으면 총 조회수를 만들지 않는다")
    fun noReportingUploadProducesNullTotal() {
        given(Triple(101L, Platform.TUMBLR, 900_000))

        assertNull(totalViews(), "근거 없는 조회수를 내보냈다")
    }

    /** **측정된 0 은 관측이다.** 업로드는 있고 아직 조회가 없었다는 뜻이다. */
    @Test
    @DisplayName("수집 플랫폼의 실제 0 조회수는 0으로 보존한다")
    fun measuredZeroViewsIsPreserved() {
        given(Triple(101L, Platform.YOUTUBE, 0))

        assertEquals(0L, totalViews(), "실측 0 을 측정 불가로 감췄다")
    }

    /**
     * **이 케이스가 동기화 전 영상을 "조회수 0회" 로 내보내던 자리다.**
     *
     * 예전 계약은 여기서 `0` 이었다 — `viewsByUploadId[it.id] ?: 0L` 이 집계 행이 없는
     * 업로드에 0 을 더했기 때문이다. 그 0 은 실측이 아니라 **아직 수집되지 않은 상태**이고,
     * 바로 위 `measuredZeroViewsIsPreserved` 의 실측 0 과 완전히 같은 모양으로 나갔다.
     *
     * "수집하는 플랫폼이 없다"(대기 0 건)와도 다르다 — 이쪽은 기다리면 채워지므로
     * [VideoListResult.pendingViewUploads] 로 구분한다.
     */
    @Test
    @DisplayName("수집 플랫폼이지만 집계 행이 없으면 0이 아니라 null 이다")
    fun reportingUploadWithoutRowsIsNotZero() {
        every { videoRepository.findByUserId(userId, any(), any(), any()) } returns
            listOf(Video(id = videoId, userId = userId, title = "영상"))
        every { videoRepository.countByUserId(userId, any()) } returns 1
        every { videoUploadRepository.findByVideoIds(listOf(videoId)) } returns mapOf(
            videoId to listOf(VideoUpload(id = 101L, videoId = videoId, platform = Platform.YOUTUBE, channelId = 1L)),
        )
        every { analyticsRepository.findByVideoUploadIds(any()) } returns emptyList()

        val item = listItem()

        assertNull(item.totalViews, "수집 전 상태를 조회수 0 회로 위장했다")
        assertEquals(1, item.pendingViewUploads, "수집 대기 상태를 알리지 않았다")
    }

    /** 수집하는 업로드가 아예 없는 경우와 구분된다 — 그쪽은 기다려도 채워지지 않는다. */
    @Test
    @DisplayName("미수집 플랫폼만 있으면 대기 건수가 0이다")
    fun unsupportedOnlyReportsNoPendingUploads() {
        given(Triple(101L, Platform.TUMBLR, 900_000))

        val item = listItem()

        assertNull(item.totalViews)
        assertEquals(0, item.pendingViewUploads, "물어볼 곳이 없는데 수집 대기로 알렸다")
    }

    /**
     * **여러 수집 플랫폼 중 일부만 집계된 경우.**
     *
     * 잰 것의 합을 그대로 주고, 빠진 업로드는 대기 건수로 알린다. 측정값을 버리지도
     * (전체 `null`), 미수집을 합계에 숨기지도(0 을 더함) 않는다.
     */
    @Test
    @DisplayName("일부 업로드만 집계됐으면 측정값을 유지하고 대기 건수를 알린다")
    fun partiallyMeasuredKeepsMeasuredSumAndReportsPending() {
        every { videoRepository.findByUserId(userId, any(), any(), any()) } returns
            listOf(Video(id = videoId, userId = userId, title = "영상"))
        every { videoRepository.countByUserId(userId, any()) } returns 1
        every { videoUploadRepository.findByVideoIds(listOf(videoId)) } returns mapOf(
            videoId to listOf(
                VideoUpload(id = 101L, videoId = videoId, platform = Platform.YOUTUBE, channelId = 1L),
                VideoUpload(id = 102L, videoId = videoId, platform = Platform.TIKTOK, channelId = 1L),
            ),
        )
        // TikTok(102) 업로드는 아직 집계 전이다.
        every { analyticsRepository.findByVideoUploadIds(any()) } returns listOf(
            AnalyticsDaily(videoUploadId = 101L, date = LocalDate.now(), views = 1_000),
        )

        val item = listItem()

        assertEquals(1_000L, item.totalViews, "잰 값을 버렸다")
        assertEquals(1, item.pendingViewUploads, "합계에 빠진 업로드를 숨겼다")
    }

    /** 전부 집계됐으면 대기 건수는 0 이다 — 부분 합계로 오해할 일이 없다. */
    @Test
    @DisplayName("모두 집계됐으면 대기 건수가 0이다")
    fun fullyMeasuredReportsNoPending() {
        given(
            Triple(101L, Platform.YOUTUBE, 1_000),
            Triple(102L, Platform.TIKTOK, 2_000),
        )

        val item = listItem()

        assertEquals(3_000L, item.totalViews)
        assertEquals(0, item.pendingViewUploads)
    }

    /** 실측 0 행이 있는 업로드는 **측정된 것**이므로 대기에 들어가지 않는다. */
    @Test
    @DisplayName("실측 0 행은 수집 대기로 세지 않는다")
    fun measuredZeroIsNotCountedAsPending() {
        given(Triple(101L, Platform.YOUTUBE, 0))

        val item = listItem()

        assertEquals(0L, item.totalViews)
        assertEquals(0, item.pendingViewUploads, "실측 0 을 수집 대기로 오인했다")
    }

    /** 여러 수집 플랫폼은 정상적으로 합산된다. */
    @Test
    @DisplayName("수집 플랫폼끼리는 그대로 합산한다")
    fun reportingPlatformsAreSummed() {
        given(
            Triple(101L, Platform.YOUTUBE, 1_000),
            Triple(102L, Platform.TIKTOK, 2_000),
        )

        assertEquals(3_000L, totalViews())
    }
}
