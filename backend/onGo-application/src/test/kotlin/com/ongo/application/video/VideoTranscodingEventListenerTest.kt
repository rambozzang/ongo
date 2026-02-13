package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.VariantStatus
import com.ongo.domain.video.VideoVariant
import com.ongo.domain.video.VideoVariantRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class VideoTranscodingEventListenerTest {

    private val videoVariantRepository = mockk<VideoVariantRepository>(relaxed = true)
    private val transcodingService = mockk<TranscodingService>()

    private lateinit var listener: VideoTranscodingEventListener

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        listener = VideoTranscodingEventListener(videoVariantRepository, transcodingService)
    }

    private fun createEvent(
        videoId: Long = 1L,
        userId: Long = 100L,
        fileUrl: String = "https://storage.example.com/videos/1/original.mp4",
        platforms: List<Platform> = listOf(Platform.YOUTUBE),
    ) = VideoTranscodingEvent(videoId, userId, fileUrl, platforms)

    private fun createVariant(
        id: Long = 10L,
        videoId: Long = 1L,
        platform: Platform = Platform.YOUTUBE,
        status: VariantStatus = VariantStatus.PENDING,
    ) = VideoVariant(id = id, videoId = videoId, platform = platform, status = status)

    @Test
    fun `should create PENDING variant when no existing variant`() {
        val event = createEvent(platforms = listOf(Platform.YOUTUBE))
        val savedVariant = createVariant(id = 10L)

        every { videoVariantRepository.findByVideoIdAndPlatform(1L, Platform.YOUTUBE) } returns null
        every { videoVariantRepository.save(any()) } returns savedVariant
        every { videoVariantRepository.findById(10L) } returns savedVariant
        every { videoVariantRepository.update(any()) } answers { firstArg() }
        every { transcodingService.transcode(any(), any(), any(), any()) } returns TranscodeResult(
            fileUrl = "https://storage/videos/1/variants/youtube/video.mp4",
            fileSizeBytes = 50_000_000L,
            width = 1920,
            height = 1080,
        )

        listener.handleTranscoding(event)

        verify {
            videoVariantRepository.save(match {
                it.videoId == 1L && it.platform == Platform.YOUTUBE && it.status == VariantStatus.PENDING
            })
        }
    }

    @Test
    fun `should reset existing variant to PENDING on retry`() {
        val event = createEvent(platforms = listOf(Platform.TIKTOK))
        val existing = createVariant(id = 20L, platform = Platform.TIKTOK, status = VariantStatus.FAILED)

        every { videoVariantRepository.findByVideoIdAndPlatform(1L, Platform.TIKTOK) } returns existing
        every { videoVariantRepository.update(any()) } answers { firstArg() }
        every { videoVariantRepository.findById(20L) } returns existing.copy(status = VariantStatus.PROCESSING)
        every { transcodingService.transcode(any(), any(), any(), any()) } returns TranscodeResult(
            fileUrl = "https://storage/videos/1/variants/tiktok/video.mp4",
            fileSizeBytes = 30_000_000L,
            width = 1080,
            height = 1920,
        )

        listener.handleTranscoding(event)

        verify {
            videoVariantRepository.update(match {
                it.id == 20L && it.status == VariantStatus.PENDING && it.errorMessage == null
            })
        }
    }

    @Test
    fun `should update variant to PROCESSING then COMPLETED on success`() {
        val event = createEvent(platforms = listOf(Platform.YOUTUBE))
        val savedVariant = createVariant(id = 10L)

        every { videoVariantRepository.findByVideoIdAndPlatform(1L, Platform.YOUTUBE) } returns null
        every { videoVariantRepository.save(any()) } returns savedVariant
        every { videoVariantRepository.findById(10L) } returns savedVariant
        every { videoVariantRepository.update(any()) } answers { firstArg() }
        every { transcodingService.transcode(any(), any(), any(), any()) } returns TranscodeResult(
            fileUrl = "https://storage/videos/1/variants/youtube/video.mp4",
            fileSizeBytes = 50_000_000L,
            width = 1920,
            height = 1080,
        )

        listener.handleTranscoding(event)

        // PROCESSING 상태 업데이트 확인
        verify {
            videoVariantRepository.update(match { it.status == VariantStatus.PROCESSING })
        }

        // COMPLETED 상태 업데이트 확인
        verify {
            videoVariantRepository.update(match {
                it.status == VariantStatus.COMPLETED &&
                    it.fileUrl == "https://storage/videos/1/variants/youtube/video.mp4" &&
                    it.fileSizeBytes == 50_000_000L &&
                    it.width == 1920 &&
                    it.height == 1080
            })
        }
    }

    @Test
    fun `should update variant to FAILED when transcoding throws exception`() {
        val event = createEvent(platforms = listOf(Platform.INSTAGRAM))
        val savedVariant = createVariant(id = 30L, platform = Platform.INSTAGRAM)

        every { videoVariantRepository.findByVideoIdAndPlatform(1L, Platform.INSTAGRAM) } returns null
        every { videoVariantRepository.save(any()) } returns savedVariant
        every { videoVariantRepository.findById(30L) } returns savedVariant
        every { videoVariantRepository.update(any()) } answers { firstArg() }
        every { transcodingService.transcode(any(), any(), any(), any()) } throws RuntimeException("ffmpeg failed")

        listener.handleTranscoding(event)

        verify {
            videoVariantRepository.update(match {
                it.status == VariantStatus.FAILED && it.errorMessage == "ffmpeg failed"
            })
        }
    }

    @Test
    fun `should process multiple platforms in parallel`() {
        val platforms = listOf(Platform.YOUTUBE, Platform.TIKTOK, Platform.INSTAGRAM)
        val event = createEvent(platforms = platforms)

        platforms.forEachIndexed { idx, platform ->
            val variant = createVariant(id = (10 + idx).toLong(), platform = platform)
            every { videoVariantRepository.findByVideoIdAndPlatform(1L, platform) } returns null
            every { videoVariantRepository.save(match { it.platform == platform }) } returns variant
            every { videoVariantRepository.findById(variant.id!!) } returns variant
        }
        every { videoVariantRepository.update(any()) } answers { firstArg() }
        every { transcodingService.transcode(any(), any(), any(), any()) } answers {
            val platform = thirdArg<Platform>()
            TranscodeResult(
                fileUrl = "https://storage/videos/1/variants/${platform.name.lowercase()}/video.mp4",
                fileSizeBytes = 40_000_000L,
                width = if (platform == Platform.YOUTUBE) 1920 else 1080,
                height = if (platform == Platform.YOUTUBE) 1080 else 1920,
            )
        }

        listener.handleTranscoding(event)

        // 3개 플랫폼 모두 save 호출 확인
        verify(exactly = 3) { videoVariantRepository.save(any()) }
        // 3개 플랫폼 모두 transcode 호출 확인
        verify(exactly = 3) { transcodingService.transcode(any(), any(), any(), any()) }
    }

    @Test
    fun `should pass correct spec to transcode service`() {
        val event = createEvent(platforms = listOf(Platform.TIKTOK))
        val savedVariant = createVariant(id = 10L, platform = Platform.TIKTOK)

        every { videoVariantRepository.findByVideoIdAndPlatform(1L, Platform.TIKTOK) } returns null
        every { videoVariantRepository.save(any()) } returns savedVariant
        every { videoVariantRepository.findById(10L) } returns savedVariant
        every { videoVariantRepository.update(any()) } answers { firstArg() }
        every { transcodingService.transcode(any(), any(), any(), any()) } returns TranscodeResult(
            fileUrl = "https://storage/result.mp4",
            fileSizeBytes = 30_000_000L,
            width = 1080,
            height = 1920,
        )

        listener.handleTranscoding(event)

        verify {
            transcodingService.transcode(
                eq(event.fileUrl),
                eq(1L),
                eq(Platform.TIKTOK),
                match { it.width == 1080 && it.height == 1920 && it.bitrateKbps == 4000 },
            )
        }
    }

    @Test
    fun `one platform failure should not affect other platforms`() {
        val event = createEvent(platforms = listOf(Platform.YOUTUBE, Platform.TIKTOK))

        val ytVariant = createVariant(id = 10L, platform = Platform.YOUTUBE)
        val ttVariant = createVariant(id = 11L, platform = Platform.TIKTOK)

        every { videoVariantRepository.findByVideoIdAndPlatform(1L, Platform.YOUTUBE) } returns null
        every { videoVariantRepository.findByVideoIdAndPlatform(1L, Platform.TIKTOK) } returns null
        every { videoVariantRepository.save(match { it.platform == Platform.YOUTUBE }) } returns ytVariant
        every { videoVariantRepository.save(match { it.platform == Platform.TIKTOK }) } returns ttVariant
        every { videoVariantRepository.findById(10L) } returns ytVariant
        every { videoVariantRepository.findById(11L) } returns ttVariant
        every { videoVariantRepository.update(any()) } answers { firstArg() }

        // YouTube 실패, TikTok 성공
        every { transcodingService.transcode(any(), any(), eq(Platform.YOUTUBE), any()) } throws RuntimeException("YouTube transcode failed")
        every { transcodingService.transcode(any(), any(), eq(Platform.TIKTOK), any()) } returns TranscodeResult(
            fileUrl = "https://storage/tiktok.mp4",
            fileSizeBytes = 30_000_000L,
            width = 1080,
            height = 1920,
        )

        listener.handleTranscoding(event)

        // YouTube는 FAILED
        verify {
            videoVariantRepository.update(match {
                it.id == 10L && it.status == VariantStatus.FAILED
            })
        }
        // TikTok은 COMPLETED
        verify {
            videoVariantRepository.update(match {
                it.id == 11L && it.status == VariantStatus.COMPLETED
            })
        }
    }
}
