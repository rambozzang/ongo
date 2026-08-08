package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoUploadPollerTest {
    private val service = mockk<PlatformUploadService>()
    private val uploads = mockk<VideoUploadRepository>(relaxed = true)
    private val videos = mockk<VideoRepository>(relaxed = true)
    private val events = mockk<ApplicationEventPublisher>(relaxed = true)
    private lateinit var poller: VideoUploadPoller

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        poller = VideoUploadPoller(listOf(service), uploads, videos, events)
    }

    @Test
    fun `PUBLISH_COMPLETE 상태는 사용 가능한 URL과 PUBLISHED로 저장된다`() {
        val due = VideoUpload(
            id = 10L,
            videoId = 1L,
            platform = Platform.TIKTOK,
            platformVideoId = "publish-1",
            status = UploadStatus.PROCESSING,
            pollToken = "publish-1",
        )
        val claimed = due.copy(attemptCount = 2, leaseOwner = "poll-owner")
        every { uploads.findDueProcessingUploads(any()) } returns listOf(due)
        every { uploads.claim(eq(10L), any(), any(), any()) } returns claimed
        every { videos.findById(1L) } returns Video(id = 1L, userId = 7L, title = "영상")
        every { service.supports(Platform.TIKTOK) } returns true
        every { service.poll(Platform.TIKTOK, "publish-1", 7L) } returns PlatformUploadResult(
            success = true,
            platformVideoId = "video-1",
            platformUrl = "https://www.tiktok.com/video/video-1",
            published = true,
        )
        every { uploads.updateOwned(any(), any()) } returns true
        every { uploads.findByVideoId(1L) } returns listOf(
            due.copy(status = UploadStatus.PUBLISHED, platformVideoId = "video-1", platformUrl = "https://www.tiktok.com/video/video-1")
        )

        poller.pollDueUploads()

        val saved = slot<VideoUpload>()
        verify { uploads.updateOwned(capture(saved), any()) }
        assertEquals(UploadStatus.PUBLISHED, saved.captured.status)
        assertEquals("video-1", saved.captured.platformVideoId)
        assertEquals("https://www.tiktok.com/video/video-1", saved.captured.platformUrl)
        assertTrue(saved.captured.pollToken == null)
        verify { events.publishEvent(match<UploadCompletedEvent> { it.success && it.platform == Platform.TIKTOK }) }
    }

    @Test
    fun `처리 중 상태는 재전송하지 않고 다음 polling 시각만 예약한다`() {
        val now = LocalDateTime.now()
        val due = VideoUpload(
            id = 11L,
            videoId = 2L,
            platform = Platform.INSTAGRAM,
            platformVideoId = "container-1",
            status = UploadStatus.PROCESSING,
            pollToken = "container-1",
        )
        every { uploads.findDueProcessingUploads(any()) } returns listOf(due)
        every { uploads.claim(eq(11L), any(), any(), any()) } returns due.copy(attemptCount = 1)
        every { videos.findById(2L) } returns Video(id = 2L, userId = 8L, title = "영상")
        every { service.supports(Platform.INSTAGRAM) } returns true
        every { service.poll(Platform.INSTAGRAM, "container-1", 8L) } returns PlatformUploadResult(
            success = true,
            platformVideoId = "container-1",
            pollToken = "container-1",
            published = false,
        )
        every { uploads.updateOwned(any(), any()) } returns true
        every { uploads.findByVideoId(2L) } returns listOf(due)

        poller.pollDueUploads()

        val saved = slot<VideoUpload>()
        verify { uploads.updateOwned(capture(saved), any()) }
        assertEquals(UploadStatus.PROCESSING, saved.captured.status)
        assertEquals("container-1", saved.captured.pollToken)
        assertTrue(saved.captured.nextRetryAt?.isAfter(now) == true)
        verify(exactly = 0) { events.publishEvent(any<UploadCompletedEvent>()) }
    }
}
