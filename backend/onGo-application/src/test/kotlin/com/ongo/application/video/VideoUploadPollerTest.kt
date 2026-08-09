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
import kotlin.test.assertFailsWith
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
            channelId = 77L,
        )
        val claimed = due.copy(attemptCount = 2, leaseOwner = "poll-owner")
        every { uploads.findDueProcessingUploads(any()) } returns listOf(due)
        every { uploads.claimForStatusCheck(eq(10L), any(), any(), any()) } returns claimed
        every { videos.findById(1L) } returns Video(id = 1L, userId = 7L, title = "영상")
        every { service.supports(Platform.TIKTOK) } returns true
        every { service.poll(Platform.TIKTOK, "publish-1", 7L, any(), eq(77L)) } returns PlatformUploadResult(
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
        every { uploads.claimForStatusCheck(eq(11L), any(), any(), any()) } returns due.copy(attemptCount = 1)
        every { videos.findById(2L) } returns Video(id = 2L, userId = 8L, title = "영상")
        every { service.supports(Platform.INSTAGRAM) } returns true
        every { service.poll(Platform.INSTAGRAM, "container-1", 8L, any(), any()) } returns PlatformUploadResult(
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

    @Test
    fun `확인 불가 상태 재확인은 새 업로드 없이 상태 조회만 한다`() {
        val upload = VideoUpload(
            id = 12L,
            videoId = 3L,
            platform = Platform.THREADS,
            platformVideoId = "thread-1",
            status = UploadStatus.UNCONFIRMED,
        )
        every { videos.findById(3L) } returns Video(id = 3L, userId = 9L, title = "영상")
        every { uploads.findByVideoIdAndPlatform(3L, Platform.THREADS) } returns upload
        every { uploads.claimForStatusCheck(eq(12L), any(), any(), any()) } returns upload.copy(attemptCount = 1)
        every { service.supports(Platform.THREADS) } returns true
        every { service.poll(Platform.THREADS, "thread-1", 9L, any(), any()) } returns PlatformUploadResult(
            success = true,
            platformVideoId = "thread-1",
            platformUrl = "https://www.threads.net/post/thread-1",
            published = true,
        )
        every { uploads.updateOwned(any(), any()) } returns true
        every { uploads.findByVideoId(3L) } returns listOf(upload.copy(status = UploadStatus.PUBLISHED))

        poller.recheck(9L, 3L, Platform.THREADS)

        verify(exactly = 1) { service.poll(Platform.THREADS, "thread-1", 9L, any(), any()) }
        verify(exactly = 0) { service.upload(any(), any(), any()) }
        verify { uploads.updateOwned(match { it.status == UploadStatus.PUBLISHED }, any()) }
    }

    @Test
    fun `확인 불가 상태에 식별자가 없으면 중복 방지를 위해 재전송하지 않는다`() {
        val upload = VideoUpload(
            id = 13L,
            videoId = 4L,
            platform = Platform.INSTAGRAM,
            status = UploadStatus.UNCONFIRMED,
        )
        every { videos.findById(4L) } returns Video(id = 4L, userId = 10L, title = "영상")
        every { uploads.findByVideoIdAndPlatform(4L, Platform.INSTAGRAM) } returns upload

        assertFailsWith<IllegalStateException> {
            poller.recheck(10L, 4L, Platform.INSTAGRAM)
        }

        verify(exactly = 0) { service.poll(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { service.upload(any(), any(), any()) }
    }

    @Test
    fun `지원되지 않는 플랫폼은 외부 업로드 없이 FAILED로 종료한다`() {
        val due = VideoUpload(
            id = 14L,
            videoId = 5L,
            platform = Platform.NAVER_CLIP,
            status = UploadStatus.PROCESSING,
            pollToken = "naver-1",
        )
        every { uploads.findDueProcessingUploads(any()) } returns listOf(due)
        every { videos.findById(5L) } returns Video(id = 5L, userId = 11L, title = "영상")
        every { service.supports(Platform.NAVER_CLIP) } returns false
        every { uploads.findByVideoId(5L) } returns listOf(due.copy(status = UploadStatus.FAILED))

        poller.pollDueUploads()

        verify { uploads.update(match { it.id == 14L && it.status == UploadStatus.FAILED }) }
        verify { events.publishEvent(match<UploadCompletedEvent> {
            it.platform == Platform.NAVER_CLIP && !it.success
        }) }
        verify(exactly = 0) { service.poll(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `상태 조회가 반복 실패하면 자동 재전송 없이 UNCONFIRMED로 고정한다`() {
        val due = VideoUpload(
            id = 15L,
            videoId = 6L,
            platform = Platform.THREADS,
            platformVideoId = "thread-15",
            pollToken = "thread-15",
            status = UploadStatus.PROCESSING,
        )
        val claimed = due.copy(attemptCount = 12, leaseOwner = "poll-owner")
        every { uploads.findDueProcessingUploads(any()) } returns listOf(due)
        every { uploads.claimForStatusCheck(eq(15L), any(), any(), any()) } returns claimed
        every { videos.findById(6L) } returns Video(id = 6L, userId = 12L, title = "영상")
        every { service.supports(Platform.THREADS) } returns true
        every { service.poll(Platform.THREADS, "thread-15", 12L, any(), any()) } throws
            java.net.SocketTimeoutException("provider timeout")
        every { uploads.updateOwned(any(), any()) } returns true
        every { uploads.findByVideoId(6L) } returns listOf(due.copy(status = UploadStatus.UNCONFIRMED))

        poller.pollDueUploads()

        val saved = slot<VideoUpload>()
        verify { uploads.updateOwned(capture(saved), match { it.startsWith("poll:15:") }) }
        assertEquals(UploadStatus.UNCONFIRMED, saved.captured.status)
        assertTrue(saved.captured.errorMessage!!.contains("게시 결과 확인 실패"))
        verify { events.publishEvent(match<UploadCompletedEvent> {
            it.platform == Platform.THREADS && !it.success
        }) }
        verify(exactly = 0) { service.upload(any(), any(), any()) }
    }

    @Test
    fun `소유하지 않은 영상의 게시 결과 재확인은 차단한다`() {
        every { videos.findById(7L) } returns Video(id = 7L, userId = 13L, title = "다른 사람 영상")

        assertFailsWith<com.ongo.common.exception.ForbiddenException> {
            poller.recheck(99L, 7L, Platform.YOUTUBE)
        }

        verify(exactly = 0) { uploads.findByVideoIdAndPlatform(any(), any()) }
        verify(exactly = 0) { service.poll(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `동일 플랫폼 다중 계정은 upload id로 게시 결과를 재확인한다`() {
        val upload = VideoUpload(
            id = 18L,
            videoId = 10L,
            platform = Platform.INSTAGRAM,
            platformVideoId = "container-18",
            status = UploadStatus.UNCONFIRMED,
            channelId = 801L,
        )
        every { videos.findById(10L) } returns Video(id = 10L, userId = 16L, title = "계정별 영상")
        every { uploads.findById(18L) } returns upload
        every { uploads.claimForStatusCheck(eq(18L), any(), any(), any()) } returns upload.copy(attemptCount = 2)
        every { service.supports(Platform.INSTAGRAM) } returns true
        every { service.poll(Platform.INSTAGRAM, "container-18", 16L, any(), eq(801L)) } returns
            PlatformUploadResult(
                success = true,
                platformVideoId = "post-18",
                platformUrl = "https://www.instagram.com/p/post-18",
                published = true,
            )
        every { uploads.updateOwned(any(), any()) } returns true
        every { uploads.findByVideoId(10L) } returns listOf(upload.copy(status = UploadStatus.PUBLISHED))

        poller.recheckUpload(16L, 10L, 18L)

        verify(exactly = 1) { service.poll(Platform.INSTAGRAM, "container-18", 16L, any(), eq(801L)) }
        verify { uploads.updateOwned(match { it.status == UploadStatus.PUBLISHED }, any()) }
        verify(exactly = 0) { service.upload(any(), any(), any()) }
    }

    @Test
    fun `상태 조회가 명시적으로 실패하면 FAILED로 종료하고 재전송하지 않는다`() {
        val due = VideoUpload(
            id = 19L,
            videoId = 11L,
            platform = Platform.YOUTUBE,
            platformVideoId = "video-19",
            pollToken = "video-19",
            status = UploadStatus.PROCESSING,
        )
        every { uploads.findDueProcessingUploads(any()) } returns listOf(due)
        every { uploads.claimForStatusCheck(eq(19L), any(), any(), any()) } returns due.copy(attemptCount = 3)
        every { videos.findById(11L) } returns Video(id = 11L, userId = 17L, title = "실패 영상")
        every { service.supports(Platform.YOUTUBE) } returns true
        every { service.poll(Platform.YOUTUBE, "video-19", 17L, any(), any()) } returns
            PlatformUploadResult(
                success = false,
                errorMessage = "정책 위반",
                published = false,
            )
        every { uploads.updateOwned(any(), any()) } returns true
        every { uploads.findByVideoId(11L) } returns listOf(due.copy(status = UploadStatus.FAILED))

        poller.pollDueUploads()

        verify { uploads.updateOwned(match { it.status == UploadStatus.FAILED && it.errorMessage == "정책 위반" }, any()) }
        verify { events.publishEvent(match<UploadCompletedEvent> { it.platform == Platform.YOUTUBE && !it.success }) }
        verify(exactly = 0) { service.upload(any(), any(), any()) }
    }

    @Test
    fun `일부 채널만 게시되면 전체 영상은 PARTIALLY_PUBLISHED가 된다`() {
        val due = VideoUpload(
            id = 20L,
            videoId = 12L,
            platform = Platform.TWITTER,
            platformVideoId = "tweet-20",
            pollToken = "tweet-20",
            status = UploadStatus.PROCESSING,
        )
        every { uploads.findDueProcessingUploads(any()) } returns listOf(due)
        every { uploads.claimForStatusCheck(eq(20L), any(), any(), any()) } returns due
        every { videos.findById(12L) } returns Video(id = 12L, userId = 18L, title = "부분 게시 영상")
        every { service.supports(Platform.TWITTER) } returns true
        every { service.poll(Platform.TWITTER, "tweet-20", 18L, any(), any()) } returns
            PlatformUploadResult(success = false, errorMessage = "일시 차단", published = false)
        every { uploads.updateOwned(any(), any()) } returns true
        every { uploads.findByVideoId(12L) } returns listOf(
            due.copy(status = UploadStatus.FAILED),
            due.copy(id = 21L, status = UploadStatus.PUBLISHED, platformUrl = "https://x.com/ongo/status/21"),
        )

        poller.pollDueUploads()

        verify { videos.update(match { it.id == 12L && it.status == UploadStatus.PARTIALLY_PUBLISHED }) }
    }

    @Test
    fun `외부 응답을 확인할 수 없으면 UNCONFIRMED로 고정한다`() {
        val due = VideoUpload(
            id = 22L,
            videoId = 13L,
            platform = Platform.TIKTOK,
            platformVideoId = "publish-22",
            pollToken = "publish-22",
            status = UploadStatus.PROCESSING,
        )
        every { uploads.findDueProcessingUploads(any()) } returns listOf(due)
        every { uploads.claimForStatusCheck(eq(22L), any(), any(), any()) } returns due
        every { videos.findById(13L) } returns Video(id = 13L, userId = 19L, title = "확인 불가 영상")
        every { service.supports(Platform.TIKTOK) } returns true
        every { service.poll(Platform.TIKTOK, "publish-22", 19L, any(), any()) } returns
            PlatformUploadResult(
                success = false,
                platformVideoId = "publish-22",
                pollToken = "publish-22",
                errorMessage = "응답 본문 손실",
                published = false,
                confirmation = PublishConfirmation.UNKNOWN,
            )
        every { uploads.updateOwned(any(), any()) } returns true
        every { uploads.findByVideoId(13L) } returns listOf(due.copy(status = UploadStatus.UNCONFIRMED))

        poller.pollDueUploads()

        verify { uploads.updateOwned(match { it.status == UploadStatus.UNCONFIRMED }, any()) }
        verify { events.publishEvent(match<UploadCompletedEvent> { it.platform == Platform.TIKTOK && !it.success }) }
        verify(exactly = 0) { service.upload(any(), any(), any()) }
    }

    @Test
    fun `상태 조회 일시 오류는 재전송 없이 PROCESSING 재시도로 남긴다`() {
        val due = VideoUpload(
            id = 16L,
            videoId = 8L,
            platform = Platform.INSTAGRAM,
            platformVideoId = "container-16",
            pollToken = "container-16",
            status = UploadStatus.PROCESSING,
        )
        every { uploads.findDueProcessingUploads(any()) } returns listOf(due)
        every { uploads.claimForStatusCheck(eq(16L), any(), any(), any()) } returns due.copy(attemptCount = 1)
        every { videos.findById(8L) } returns Video(id = 8L, userId = 14L, title = "영상")
        every { service.supports(Platform.INSTAGRAM) } returns true
        every { service.poll(Platform.INSTAGRAM, "container-16", 14L, any(), any()) } throws
            java.io.IOException("temporary provider failure")
        every { uploads.updateOwned(any(), any()) } returns true
        every { uploads.findByVideoId(8L) } returns listOf(due)

        poller.pollDueUploads()

        verify { uploads.updateOwned(match {
            it.status == UploadStatus.PROCESSING &&
                it.errorMessage!!.contains("상태 확인 재시도 예정") &&
                it.nextRetryAt != null
        }, any()) }
        verify(exactly = 0) { events.publishEvent(any<UploadCompletedEvent>()) }
        verify(exactly = 0) { service.upload(any(), any(), any()) }
    }

    @Test
    fun `다른 작업자가 lease를 가져갔으면 상태 조회를 시작하지 않는다`() {
        val due = VideoUpload(
            id = 17L,
            videoId = 9L,
            platform = Platform.YOUTUBE,
            platformVideoId = "video-17",
            pollToken = "video-17",
            status = UploadStatus.PROCESSING,
        )
        every { uploads.findDueProcessingUploads(any()) } returns listOf(due)
        every { videos.findById(9L) } returns Video(id = 9L, userId = 15L, title = "영상")
        every { service.supports(Platform.YOUTUBE) } returns true
        every { uploads.claimForStatusCheck(eq(17L), any(), any(), any()) } returns null

        poller.pollDueUploads()

        verify(exactly = 0) { service.poll(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { uploads.updateOwned(any(), any()) }
    }
}
