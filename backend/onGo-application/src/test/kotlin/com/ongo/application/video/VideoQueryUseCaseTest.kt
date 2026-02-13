package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.VariantStatus
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.video.*
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VideoQueryUseCaseTest {

    private val videoRepository = mockk<VideoRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val videoPlatformMetaRepository = mockk<VideoPlatformMetaRepository>()
    private val videoVariantRepository = mockk<VideoVariantRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private lateinit var useCase: VideoQueryUseCase

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = VideoQueryUseCase(
            videoRepository, videoUploadRepository, videoPlatformMetaRepository,
            videoVariantRepository, eventPublisher,
        )
    }

    private fun createVideo(
        id: Long = 1L,
        userId: Long = 100L,
        fileUrl: String? = "https://storage/videos/1/video.mp4",
        status: UploadStatus = UploadStatus.DRAFT,
    ) = Video(
        id = id,
        userId = userId,
        title = "test video",
        description = "description",
        tags = listOf("tag1"),
        fileUrl = fileUrl,
        fileSizeBytes = 50_000_000L,
        status = status,
    )

    private fun createVariant(
        videoId: Long = 1L,
        platform: Platform = Platform.YOUTUBE,
        status: VariantStatus = VariantStatus.COMPLETED,
        fileUrl: String? = "https://storage/variants/youtube/video.mp4",
    ) = VideoVariant(
        id = 10L,
        videoId = videoId,
        platform = platform,
        status = status,
        fileUrl = fileUrl,
        fileSizeBytes = 40_000_000L,
        width = 1920,
        height = 1080,
    )

    // ---- getVideoDetail tests ----

    @Test
    fun `getVideoDetail should include variants in response`() {
        val video = createVideo()
        val variants = listOf(
            createVariant(platform = Platform.YOUTUBE, status = VariantStatus.COMPLETED),
            createVariant(platform = Platform.TIKTOK, status = VariantStatus.PROCESSING,
                fileUrl = null).copy(id = 11L, width = 1080, height = 1920),
        )

        every { videoRepository.findById(1L) } returns video
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
        every { videoPlatformMetaRepository.findByVideoUploadIds(any()) } returns emptyMap()
        every { videoVariantRepository.findByVideoId(1L) } returns variants

        val result = useCase.getVideoDetail(100L, 1L)

        assertEquals(2, result.variants.size)
        assertEquals(Platform.YOUTUBE, result.variants[0].platform)
        assertEquals(VariantStatus.COMPLETED, result.variants[0].status)
        assertEquals(Platform.TIKTOK, result.variants[1].platform)
        assertEquals(VariantStatus.PROCESSING, result.variants[1].status)
    }

    @Test
    fun `getVideoDetail should return empty variants when none exist`() {
        val video = createVideo()

        every { videoRepository.findById(1L) } returns video
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
        every { videoPlatformMetaRepository.findByVideoUploadIds(any()) } returns emptyMap()
        every { videoVariantRepository.findByVideoId(1L) } returns emptyList()

        val result = useCase.getVideoDetail(100L, 1L)

        assertTrue(result.variants.isEmpty())
    }

    @Test
    fun `getVideoDetail should throw NotFoundException for non-existent video`() {
        every { videoRepository.findById(999L) } returns null

        assertFailsWith<NotFoundException> {
            useCase.getVideoDetail(100L, 999L)
        }
    }

    @Test
    fun `getVideoDetail should throw ForbiddenException for wrong user`() {
        val video = createVideo(userId = 100L)
        every { videoRepository.findById(1L) } returns video

        assertFailsWith<ForbiddenException> {
            useCase.getVideoDetail(999L, 1L)
        }
    }

    // ---- retryTranscode tests ----

    @Test
    fun `retryTranscode should publish VideoTranscodingEvent for FAILED variant`() {
        val video = createVideo(id = 1L, userId = 100L, fileUrl = "https://storage/file.mp4")
        val variant = createVariant(platform = Platform.YOUTUBE, status = VariantStatus.FAILED)

        every { videoRepository.findById(1L) } returns video
        every { videoVariantRepository.findByVideoIdAndPlatform(1L, Platform.YOUTUBE) } returns variant

        useCase.retryTranscode(100L, 1L, "YOUTUBE")

        verify {
            eventPublisher.publishEvent(match<VideoTranscodingEvent> {
                it.videoId == 1L &&
                    it.userId == 100L &&
                    it.fileUrl == "https://storage/file.mp4" &&
                    it.platforms == listOf(Platform.YOUTUBE)
            })
        }
    }

    @Test
    fun `retryTranscode should throw when variant is not FAILED`() {
        val video = createVideo(id = 1L, userId = 100L)
        val variant = createVariant(platform = Platform.YOUTUBE, status = VariantStatus.COMPLETED)

        every { videoRepository.findById(1L) } returns video
        every { videoVariantRepository.findByVideoIdAndPlatform(1L, Platform.YOUTUBE) } returns variant

        assertFailsWith<IllegalStateException> {
            useCase.retryTranscode(100L, 1L, "YOUTUBE")
        }
    }

    @Test
    fun `retryTranscode should allow when no variant exists yet`() {
        val video = createVideo(id = 1L, userId = 100L, fileUrl = "https://storage/file.mp4")

        every { videoRepository.findById(1L) } returns video
        every { videoVariantRepository.findByVideoIdAndPlatform(1L, Platform.TIKTOK) } returns null

        useCase.retryTranscode(100L, 1L, "TIKTOK")

        verify {
            eventPublisher.publishEvent(match<VideoTranscodingEvent> {
                it.platforms == listOf(Platform.TIKTOK)
            })
        }
    }

    @Test
    fun `retryTranscode should throw when video has no fileUrl`() {
        val video = createVideo(id = 1L, userId = 100L, fileUrl = null)

        every { videoRepository.findById(1L) } returns video
        every { videoVariantRepository.findByVideoIdAndPlatform(1L, Platform.YOUTUBE) } returns null

        assertFailsWith<IllegalStateException> {
            useCase.retryTranscode(100L, 1L, "YOUTUBE")
        }
    }

    @Test
    fun `retryTranscode should throw ForbiddenException for wrong user`() {
        val video = createVideo(id = 1L, userId = 100L)
        every { videoRepository.findById(1L) } returns video

        assertFailsWith<ForbiddenException> {
            useCase.retryTranscode(999L, 1L, "YOUTUBE")
        }
    }

    // ---- deleteVideo tests ----

    @Test
    fun `deleteVideo should delete variants before video`() {
        val video = createVideo(id = 1L, userId = 100L)

        every { videoRepository.findById(1L) } returns video
        every { videoVariantRepository.deleteByVideoId(1L) } just runs
        every { videoRepository.delete(1L) } just runs

        useCase.deleteVideo(100L, 1L)

        verifyOrder {
            videoVariantRepository.deleteByVideoId(1L)
            videoRepository.delete(1L)
        }
    }

    @Test
    fun `deleteVideo should throw NotFoundException for non-existent video`() {
        every { videoRepository.findById(999L) } returns null

        assertFailsWith<NotFoundException> {
            useCase.deleteVideo(100L, 999L)
        }
    }

    @Test
    fun `deleteVideo should throw ForbiddenException for wrong user`() {
        val video = createVideo(id = 1L, userId = 100L)
        every { videoRepository.findById(1L) } returns video

        assertFailsWith<ForbiddenException> {
            useCase.deleteVideo(999L, 1L)
        }
    }
}
