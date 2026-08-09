package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.Visibility
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

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
}
