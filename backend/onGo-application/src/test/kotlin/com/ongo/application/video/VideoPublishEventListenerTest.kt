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

        every { ytService.upload(any(), any(), any()) } returns PlatformUploadResult(success = true)
        every { ttService.upload(any(), any(), any()) } returns PlatformUploadResult(success = true)
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
}
