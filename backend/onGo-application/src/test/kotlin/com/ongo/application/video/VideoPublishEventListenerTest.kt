package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.VariantStatus
import com.ongo.common.enums.Visibility
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.domain.video.VideoVariant
import com.ongo.domain.video.VideoVariantRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher

class VideoPublishEventListenerTest {

    private val platformUploadServices = mutableListOf<PlatformUploadService>()
    private val videoUploadRepository = mockk<VideoUploadRepository>(relaxed = true)
    private val videoRepository = mockk<VideoRepository>(relaxed = true)
    private val videoVariantRepository = mockk<VideoVariantRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private lateinit var listener: VideoPublishEventListener

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        platformUploadServices.clear()
        listener = VideoPublishEventListener(
            platformUploadServices, videoUploadRepository, videoRepository,
            videoVariantRepository, eventPublisher,
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
    fun `should use variant fileUrl when variant is COMPLETED`() {
        val ytService = createMockService(Platform.YOUTUBE)
        platformUploadServices.add(ytService)

        val config = createConfig(platform = Platform.YOUTUBE)
        val event = createEvent(
            fileUrl = "https://storage/original.mp4",
            configs = listOf(config),
        )

        val completedVariant = VideoVariant(
            id = 20L,
            videoId = 1L,
            platform = Platform.YOUTUBE,
            status = VariantStatus.COMPLETED,
            fileUrl = "https://storage/variants/youtube/video.mp4",
            fileSizeBytes = 50_000_000L,
            width = 1920,
            height = 1080,
        )

        every { videoVariantRepository.findByVideoIdAndPlatform(1L, Platform.YOUTUBE) } returns completedVariant
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

        // variant의 fileUrl이 사용되었는지 확인
        verify {
            ytService.upload(any(), eq("https://storage/variants/youtube/video.mp4"), any())
        }
    }

    @Test
    fun `should fallback to original fileUrl when variant is not COMPLETED`() {
        val ttService = createMockService(Platform.TIKTOK)
        platformUploadServices.add(ttService)

        val config = createConfig(platform = Platform.TIKTOK, videoUploadId = 11L)
        val event = createEvent(
            fileUrl = "https://storage/original.mp4",
            configs = listOf(config),
        )

        val processingVariant = VideoVariant(
            id = 21L,
            videoId = 1L,
            platform = Platform.TIKTOK,
            status = VariantStatus.PROCESSING,
            fileUrl = null,
        )

        every { videoVariantRepository.findByVideoIdAndPlatform(1L, Platform.TIKTOK) } returns processingVariant
        every { ttService.upload(any(), any(), any()) } returns PlatformUploadResult(success = true)
        every { videoUploadRepository.findById(11L) } returns VideoUpload(
            id = 11L, videoId = 1L, platform = Platform.TIKTOK,
        )
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
        every { videoRepository.findById(1L) } returns null

        listener.handleVideoPublish(event)

        // 원본 fileUrl이 사용되었는지 확인
        verify {
            ttService.upload(any(), eq("https://storage/original.mp4"), any())
        }
    }

    @Test
    fun `should fallback to original fileUrl when no variant exists`() {
        val igService = createMockService(Platform.INSTAGRAM)
        platformUploadServices.add(igService)

        val config = createConfig(platform = Platform.INSTAGRAM, videoUploadId = 12L)
        val event = createEvent(
            fileUrl = "https://storage/original.mp4",
            configs = listOf(config),
        )

        every { videoVariantRepository.findByVideoIdAndPlatform(1L, Platform.INSTAGRAM) } returns null
        every { igService.upload(any(), any(), any()) } returns PlatformUploadResult(success = true)
        every { videoUploadRepository.findById(12L) } returns VideoUpload(
            id = 12L, videoId = 1L, platform = Platform.INSTAGRAM,
        )
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
        every { videoRepository.findById(1L) } returns null

        listener.handleVideoPublish(event)

        // 원본 fileUrl이 사용되었는지 확인
        verify {
            igService.upload(any(), eq("https://storage/original.mp4"), any())
        }
    }

    @Test
    fun `should fallback to original fileUrl when variant is FAILED`() {
        val ytService = createMockService(Platform.YOUTUBE)
        platformUploadServices.add(ytService)

        val config = createConfig(platform = Platform.YOUTUBE)
        val event = createEvent(
            fileUrl = "https://storage/original.mp4",
            configs = listOf(config),
        )

        val failedVariant = VideoVariant(
            id = 20L,
            videoId = 1L,
            platform = Platform.YOUTUBE,
            status = VariantStatus.FAILED,
            fileUrl = null,
            errorMessage = "ffmpeg failed",
        )

        every { videoVariantRepository.findByVideoIdAndPlatform(1L, Platform.YOUTUBE) } returns failedVariant
        every { ytService.upload(any(), any(), any()) } returns PlatformUploadResult(success = true)
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
    fun `should use different fileUrls per platform based on variant status`() {
        val ytService = createMockService(Platform.YOUTUBE)
        val ttService = createMockService(Platform.TIKTOK)
        platformUploadServices.addAll(listOf(ytService, ttService))

        val ytConfig = createConfig(platform = Platform.YOUTUBE, videoUploadId = 10L)
        val ttConfig = createConfig(platform = Platform.TIKTOK, videoUploadId = 11L)
        val event = createEvent(
            fileUrl = "https://storage/original.mp4",
            configs = listOf(ytConfig, ttConfig),
        )

        // YouTube: variant COMPLETED → variant fileUrl 사용
        every { videoVariantRepository.findByVideoIdAndPlatform(1L, Platform.YOUTUBE) } returns VideoVariant(
            id = 20L, videoId = 1L, platform = Platform.YOUTUBE,
            status = VariantStatus.COMPLETED, fileUrl = "https://storage/variants/youtube/video.mp4",
        )
        // TikTok: variant PENDING → 원본 fallback
        every { videoVariantRepository.findByVideoIdAndPlatform(1L, Platform.TIKTOK) } returns VideoVariant(
            id = 21L, videoId = 1L, platform = Platform.TIKTOK,
            status = VariantStatus.PENDING, fileUrl = null,
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
            ytService.upload(any(), eq("https://storage/variants/youtube/video.mp4"), any())
        }
        verify {
            ttService.upload(any(), eq("https://storage/original.mp4"), any())
        }
    }
}
