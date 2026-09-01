package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.Visibility
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VideoPublishEventListenerTest {

    private val platformUploadServices = mutableListOf<PlatformUploadService>()
    private val videoUploadRepository = mockk<VideoUploadRepository>(relaxed = true)
    private val videoRepository = mockk<VideoRepository>(relaxed = true)
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private lateinit var listener: VideoPublishEventListener

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        platformUploadServices.clear()
        listener = VideoPublishEventListener(
            platformUploadServices, videoUploadRepository, videoRepository,
            eventPublisher,
        )
    }

    private fun createMockService(platform: Platform): PlatformUploadService {
        val service = mockk<PlatformUploadService>()
        every { service.supports(platform) } returns true
        every { service.supports(neq(platform)) } returns false
        return service
    }

    private fun createEvent(
        videoId: Long = 1L,
        userId: Long = 100L,
        fileUrl: String = "https://storage/original.mp4",
        configs: List<PlatformUploadConfig> = emptyList(),
    ) = VideoPublishEvent(videoId, userId, fileUrl, configs)

    private fun createConfig(
        platform: Platform = Platform.YOUTUBE,
        videoUploadId: Long = 10L,
    ) = PlatformUploadConfig(
        platform = platform,
        videoUploadId = videoUploadId,
        title = "Test",
        description = "Desc",
        tags = listOf("tag"),
        visibility = Visibility.PUBLIC,
        thumbnailUrl = null,
        scheduledAt = null,
    )

    @Test
    fun `should always use original fileUrl for upload`() {
        val ytService = createMockService(Platform.YOUTUBE)
        platformUploadServices.add(ytService)

        val config = createConfig(platform = Platform.YOUTUBE)
        val event = createEvent(
            fileUrl = "https://storage/original.mp4",
            configs = listOf(config),
        )

        every { ytService.upload(any(), any(), any()) } returns PlatformUploadResult(
            success = true,
            platformVideoId = "yt_123",
            platformUrl = "https://youtube.com/watch?v=abc",
            published = false,
        )
        every { videoUploadRepository.findById(10L) } returns VideoUpload(
            id = 10L, videoId = 1L, platform = Platform.YOUTUBE,
        )
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
        every { videoRepository.findById(1L) } returns null

        listener.handleVideoPublish(event)

        verify {
            ytService.upload(any(), eq("https://storage/original.mp4"), any())
        }
    }

    @Test
    fun `should upload to multiple platforms with original file`() {
        val ytService = createMockService(Platform.YOUTUBE)
        val ttService = createMockService(Platform.TIKTOK)
        platformUploadServices.addAll(listOf(ytService, ttService))

        val ytConfig = createConfig(platform = Platform.YOUTUBE, videoUploadId = 10L)
        val ttConfig = createConfig(platform = Platform.TIKTOK, videoUploadId = 11L)
        val event = createEvent(
            fileUrl = "https://storage/original.mp4",
            configs = listOf(ytConfig, ttConfig),
        )

        every { ytService.upload(any(), any(), any()) } returns PlatformUploadResult(success = true, published = false)
        every { ttService.upload(any(), any(), any()) } returns PlatformUploadResult(success = true, published = false)
        every { videoUploadRepository.findById(any()) } returns VideoUpload(
            id = 10L, videoId = 1L, platform = Platform.YOUTUBE,
        )
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
        every { videoRepository.findById(1L) } returns null

        listener.handleVideoPublish(event)

        verify {
            ytService.upload(any(), eq("https://storage/original.mp4"), any())
        }
        verify {
            ttService.upload(any(), eq("https://storage/original.mp4"), any())
        }
    }

    @Test
    fun `should start all seven platform uploads concurrently`() {
        val platforms = listOf(
            Platform.YOUTUBE,
            Platform.TIKTOK,
            Platform.NAVER_CLIP,
            Platform.TWITTER,
            Platform.INSTAGRAM,
            Platform.THREADS,
            Platform.FACEBOOK,
        )
        val services = platforms.map(::createMockService)
        platformUploadServices.addAll(services)
        val barrier = CyclicBarrier(platforms.size)

        services.forEachIndexed { index, service ->
            every { service.upload(any(), eq("https://storage/original.mp4"), eq(100L)) } answers {
                barrier.await(5, TimeUnit.SECONDS)
                PlatformUploadResult(
                    success = true,
                    platformVideoId = "platform-$index",
                    platformUrl = "https://platform.test/$index",
                    published = true,
                )
            }
        }
        every { videoUploadRepository.claim(any(), any(), any(), any()) } answers {
            VideoUpload(
                id = firstArg(),
                videoId = 1L,
                platform = Platform.YOUTUBE,
            )
        }
        every { videoUploadRepository.findById(any()) } answers {
            VideoUpload(
                id = firstArg(),
                videoId = 1L,
                platform = Platform.YOUTUBE,
            )
        }
        every { videoUploadRepository.updateOwned(any(), any()) } returns true
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
        every { videoRepository.findById(1L) } returns null

        val event = createEvent(
            userId = 100L,
            configs = platforms.mapIndexed { index, platform ->
                createConfig(platform = platform, videoUploadId = 10L + index)
            },
        )

        listener.handleVideoPublish(event)

        services.forEach { service ->
            verify(exactly = 1) { service.upload(any(), eq("https://storage/original.mp4"), eq(100L)) }
        }
    }

    @Test
    fun `should expose a partial publish when one platform succeeds and another fails`() {
        val ytService = createMockService(Platform.YOUTUBE)
        val ttService = createMockService(Platform.TIKTOK)
        platformUploadServices.addAll(listOf(ytService, ttService))

        every { ytService.upload(any(), any(), any()) } returns PlatformUploadResult(
            success = true,
            platformVideoId = "yt-1",
            platformUrl = "https://youtube.test/yt-1",
            published = true,
        )
        every { ttService.upload(any(), any(), any()) } returns PlatformUploadResult(
            success = false,
            errorMessage = "토큰이 만료되었습니다.",
            published = false,
        )

        val uploads = ConcurrentHashMap<Long, VideoUpload>().apply {
            put(10L, VideoUpload(id = 10L, videoId = 1L, platform = Platform.YOUTUBE))
            put(11L, VideoUpload(id = 11L, videoId = 1L, platform = Platform.TIKTOK))
        }
        every { videoUploadRepository.claim(any(), any(), any(), any()) } answers {
            uploads[firstArg<Long>()]
        }
        every { videoUploadRepository.findById(any()) } answers { uploads[firstArg<Long>()] }
        every { videoUploadRepository.updateOwned(any(), any()) } answers {
            val updated = firstArg<VideoUpload>()
            uploads[updated.id!!] = updated
            true
        }
        every { videoUploadRepository.findByVideoId(1L) } answers { uploads.values.toList() }
        val originalVideo = com.ongo.domain.video.Video(
            id = 1L,
            userId = 100L,
            title = "원본",
            fileUrl = "https://storage/original.mp4",
        )
        every { videoRepository.findById(1L) } returns originalVideo
        val updatedVideo = slot<com.ongo.domain.video.Video>()
        every { videoRepository.update(capture(updatedVideo)) } answers { firstArg() }

        listener.handleVideoPublish(
            createEvent(
                configs = listOf(
                    createConfig(Platform.YOUTUBE, 10L),
                    createConfig(Platform.TIKTOK, 11L),
                ),
            ),
        )

        assertEquals(UploadStatus.PUBLISHED, uploads[10L]?.status)
        assertEquals(UploadStatus.FAILED, uploads[11L]?.status)
        assertEquals(UploadStatus.PARTIALLY_PUBLISHED, updatedVideo.captured.status)
    }

    @Test
    fun `temporary provider failure is persisted as a durable retry instead of immediate failure`() {
        val ytService = createMockService(Platform.YOUTUBE)
        platformUploadServices.add(ytService)
        val upload = VideoUpload(
            id = 10L,
            videoId = 1L,
            platform = Platform.YOUTUBE,
            status = UploadStatus.UPLOADING,
            attemptCount = 1,
        )
        val uploads = ConcurrentHashMap<Long, VideoUpload>().apply { put(10L, upload) }
        every { ytService.upload(any(), any(), any()) } returns PlatformUploadResult(
            success = false,
            published = false,
            errorMessage = "429 Too Many Requests",
            retryable = true,
            retryAfter = Duration.ofSeconds(30),
            httpStatus = 429,
        )
        every { videoUploadRepository.claim(10L, any(), any(), any()) } returns upload.copy(
            leaseOwner = "worker",
            leaseUntil = java.time.LocalDateTime.now().plusMinutes(5),
        )
        every { videoUploadRepository.findById(10L) } answers { uploads[10L] }
        every { videoUploadRepository.updateOwned(any(), any()) } answers {
            uploads[10L] = firstArg()
            true
        }
        every { videoUploadRepository.findByVideoId(1L) } answers { uploads.values.toList() }
        every { videoRepository.findById(1L) } returns Video(id = 1L, userId = 100L, title = "원본")

        listener.handleVideoPublish(createEvent(configs = listOf(createConfig())))

        assertEquals(UploadStatus.UPLOADING, uploads[10L]?.status)
        assertEquals(true, uploads[10L]?.nextRetryAt?.isAfter(java.time.LocalDateTime.now()) == true)
        verify(exactly = 0) { eventPublisher.publishEvent(any<UploadCompletedEvent>()) }
    }

    @Test
    fun `storage preparation failure is failed without marking an external attempt unconfirmed`() {
        val ytService = createMockService(Platform.YOUTUBE)
        platformUploadServices.add(ytService)
        val storage = mockk<StorageService>()
        every { storage.getFileUrl(1L, "https://storage/original.mp4") } throws
            IllegalStateException("storage unavailable")

        val upload = VideoUpload(
            id = 10L,
            videoId = 1L,
            platform = Platform.YOUTUBE,
            status = UploadStatus.UPLOADING,
        )
        val persisted = slot<VideoUpload>()
        every { videoUploadRepository.claim(10L, any(), any(), any()) } returns upload
        every { videoUploadRepository.findById(10L) } returns upload
        every { videoUploadRepository.updateOwned(capture(persisted), any()) } returns true
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()

        val storageAwareListener = VideoPublishEventListener(
            platformUploadServices,
            videoUploadRepository,
            videoRepository,
            eventPublisher,
            storageService = storage,
        )

        storageAwareListener.handleVideoPublish(createEvent(configs = listOf(createConfig())))

        assertEquals(UploadStatus.FAILED, persisted.captured.status)
        verify(exactly = 0) { ytService.upload(any(), any(), any()) }
        verify(exactly = 1) {
            eventPublisher.publishEvent(match<UploadCompletedEvent> {
                !it.success && it.errorMessage?.contains("파일 준비 실패") == true
            })
        }
    }

    @Test
    fun `does not call an external platform after a queued upload is cancelled`() {
        val ytService = createMockService(Platform.YOUTUBE)
        platformUploadServices.add(ytService)
        val config = createConfig(Platform.YOUTUBE, 10L)

        every { videoUploadRepository.claim(10L, any(), any(), any()) } returns VideoUpload(
            id = 10L,
            videoId = 1L,
            platform = Platform.YOUTUBE,
            status = UploadStatus.UPLOADING,
        )
        every { videoUploadRepository.findById(10L) } returns VideoUpload(
            id = 10L,
            videoId = 1L,
            platform = Platform.YOUTUBE,
            status = UploadStatus.CANCELLED,
        )
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()

        listener.handleVideoPublish(createEvent(configs = listOf(config)))

        verify(exactly = 0) { ytService.upload(any(), any(), any()) }
    }

    @Test
    fun `does not upload when distributed concurrency slot cannot be acquired`() {
        val ytService = createMockService(Platform.YOUTUBE)
        platformUploadServices.add(ytService)
        every { ytService.upload(any(), any(), any()) } returns PlatformUploadResult(
            success = false,
            published = false,
            errorMessage = "should not be called",
        )
        val upload = VideoUpload(
            id = 10L,
            videoId = 1L,
            platform = Platform.YOUTUBE,
            status = UploadStatus.UPLOADING,
        )
        every { videoUploadRepository.claim(10L, any(), any(), any()) } returns upload
        every { videoUploadRepository.findById(10L) } returns upload
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()

        val lockPort = NeverAcquiresDistributedLock()
        val lockedListener = VideoPublishEventListener(
            platformUploadServices,
            videoUploadRepository,
            videoRepository,
            eventPublisher,
            distributedLockPort = lockPort,
        )

        lockedListener.handleVideoPublish(createEvent(configs = listOf(createConfig())))

        verify(exactly = 0) { ytService.upload(any(), any(), any()) }
        verify(exactly = 0) { videoUploadRepository.claim(any(), any(), any(), any()) }
    }

    /**
     * 게시 게이트가 생기기 전에 만들어진 업로드 행이 이벤트로 되살아나는 경우다.
     *
     * 게시 경로가 없는 플랫폼은 외부 호출도, lease 확보도 하지 않고 **사용자가 읽을 수 있는
     * 이유**로 FAILED 여야 한다. 예전에는 "지원되지 않는 플랫폼: NAVER_CLIP" 이라 왜 안 되는지
     * 알 수 없었고, supports 가 true 로 새면 클라이언트의 미구현 분기 문구가 그대로 나갔다.
     */
    @Test
    fun `게시 경로가 없는 플랫폼은 외부 호출 없이 정직한 사유로 FAILED 처리한다`() {
        val ytService = createMockService(Platform.YOUTUBE)
        platformUploadServices.add(ytService)

        val config = createConfig(platform = Platform.NAVER_CLIP, videoUploadId = 42L)
        every { videoUploadRepository.findById(42L) } returns VideoUpload(
            id = 42L, videoId = 1L, platform = Platform.NAVER_CLIP,
        )
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
        every { videoRepository.findById(1L) } returns null

        listener.handleVideoPublish(createEvent(configs = listOf(config)))

        val saved = slot<VideoUpload>()
        verify { videoUploadRepository.update(capture(saved)) }
        assertEquals(UploadStatus.FAILED, saved.captured.status)
        val reason = saved.captured.errorMessage
        assertNotNull(reason)
        assertTrue(reason.contains("Naver Clip"), "실패 사유에 플랫폼 설명이 없다: $reason")
        assertFalse(reason.contains("StreamPublishUseCase"), "내부 문구가 노출됐다: $reason")
        assertFalse(reason.contains("uploadVideo"), "내부 문구가 노출됐다: $reason")

        // 외부 경계에 닿지 않고, lease 도 잡지 않는다.
        verify(exactly = 0) { ytService.upload(any(), any(), any()) }
        verify(exactly = 0) { videoUploadRepository.claim(any(), any(), any(), any()) }
        verify {
            eventPublisher.publishEvent(
                match<UploadCompletedEvent> { it.platform == Platform.NAVER_CLIP && !it.success },
            )
        }
    }

    private class NeverAcquiresDistributedLock : DistributedLockPort {
        override fun <T> withAnyLock(lockIds: Collection<Long>, block: () -> T): T? = null

        override fun withLock(lockId: Long, block: () -> Unit): Boolean = false

        @Deprecated("test implementation")
        override fun tryLock(lockId: Long): Boolean = false

        @Deprecated("test implementation")
        override fun releaseLock(lockId: Long) = Unit
    }
}
