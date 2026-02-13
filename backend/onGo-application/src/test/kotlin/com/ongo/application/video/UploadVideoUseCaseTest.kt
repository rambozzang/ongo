package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.DuplicateResourceException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import kotlin.test.assertFailsWith

class UploadVideoUseCaseTest {

    private val videoRepository = mockk<VideoRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val storageService = mockk<StorageService>()
    private val channelRepository = mockk<ChannelRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private lateinit var useCase: UploadVideoUseCase

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = UploadVideoUseCase(
            videoRepository, subscriptionRepository, storageService, channelRepository, eventPublisher,
        )
    }

    private fun createVideo(
        id: Long = 1L,
        userId: Long = 100L,
        fileUrl: String? = null,
        status: UploadStatus = UploadStatus.DRAFT,
    ) = Video(
        id = id,
        userId = userId,
        title = "test video",
        fileUrl = fileUrl,
        status = status,
    )

    private fun createChannel(
        userId: Long = 100L,
        platform: Platform = Platform.YOUTUBE,
    ) = Channel(
        id = 1L,
        userId = userId,
        platform = platform,
        platformChannelId = "ch_123",
        channelName = "My Channel",
        accessToken = "token",
    )

    @Test
    fun `completeUpload should publish VideoTranscodingEvent when channels exist`() {
        val video = createVideo(id = 1L, userId = 100L)
        val channels = listOf(
            createChannel(platform = Platform.YOUTUBE),
            createChannel(platform = Platform.TIKTOK),
        )
        val fileUrl = "https://storage.example.com/videos/1/video.mp4"

        every { videoRepository.findById(1L) } returns video
        every { videoRepository.findByContentHash(any()) } returns null
        every { videoRepository.update(any()) } returns video.copy(fileUrl = fileUrl)
        every { channelRepository.findByUserId(100L) } returns channels

        useCase.completeUpload(1L, fileUrl, "hash123")

        verify {
            eventPublisher.publishEvent(match<VideoTranscodingEvent> {
                it.videoId == 1L &&
                    it.userId == 100L &&
                    it.fileUrl == fileUrl &&
                    it.platforms.size == 2 &&
                    it.platforms.contains(Platform.YOUTUBE) &&
                    it.platforms.contains(Platform.TIKTOK)
            })
        }
    }

    @Test
    fun `completeUpload should NOT publish event when no channels connected`() {
        val video = createVideo(id = 1L, userId = 100L)
        val fileUrl = "https://storage.example.com/videos/1/video.mp4"

        every { videoRepository.findById(1L) } returns video
        every { videoRepository.findByContentHash(any()) } returns null
        every { videoRepository.update(any()) } returns video.copy(fileUrl = fileUrl)
        every { channelRepository.findByUserId(100L) } returns emptyList()

        useCase.completeUpload(1L, fileUrl, "hash123")

        verify(exactly = 0) {
            eventPublisher.publishEvent(any<VideoTranscodingEvent>())
        }
    }

    @Test
    fun `completeUpload should throw NotFoundException for non-existent video`() {
        every { videoRepository.findById(999L) } returns null

        assertFailsWith<NotFoundException> {
            useCase.completeUpload(999L, "https://example.com/file.mp4", null)
        }
    }

    @Test
    fun `completeUpload should throw DuplicateResourceException for duplicate contentHash`() {
        val video = createVideo(id = 1L)
        val existing = createVideo(id = 2L) // 다른 ID

        every { videoRepository.findById(1L) } returns video
        every { videoRepository.findByContentHash("dup_hash") } returns existing

        assertFailsWith<DuplicateResourceException> {
            useCase.completeUpload(1L, "https://example.com/file.mp4", "dup_hash")
        }
    }

    @Test
    fun `completeUpload should allow same video with same contentHash`() {
        val video = createVideo(id = 1L, userId = 100L)
        val fileUrl = "https://storage.example.com/videos/1/video.mp4"

        every { videoRepository.findById(1L) } returns video
        // 같은 ID의 영상이 반환 → 중복 아님
        every { videoRepository.findByContentHash("hash123") } returns video
        every { videoRepository.update(any()) } returns video.copy(fileUrl = fileUrl)
        every { channelRepository.findByUserId(100L) } returns emptyList()

        useCase.completeUpload(1L, fileUrl, "hash123")

        verify { videoRepository.update(any()) }
    }

    @Test
    fun `completeUpload should still update video when contentHash is null`() {
        val video = createVideo(id = 1L, userId = 100L)
        val fileUrl = "https://storage.example.com/videos/1/video.mp4"

        every { videoRepository.findById(1L) } returns video
        every { videoRepository.update(any()) } returns video.copy(fileUrl = fileUrl)
        every { channelRepository.findByUserId(100L) } returns emptyList()

        useCase.completeUpload(1L, fileUrl, null)

        verify {
            videoRepository.update(match {
                it.fileUrl == fileUrl && it.contentHash == null && it.status == UploadStatus.DRAFT
            })
        }
        verify(exactly = 0) { videoRepository.findByContentHash(any()) }
    }

    @Test
    fun `completeUpload should publish event with all connected platform types`() {
        val video = createVideo(id = 5L, userId = 200L)
        val fileUrl = "https://storage.example.com/videos/5/video.mp4"
        val allChannels = listOf(
            createChannel(userId = 200L, platform = Platform.YOUTUBE),
            createChannel(userId = 200L, platform = Platform.TIKTOK),
            createChannel(userId = 200L, platform = Platform.INSTAGRAM),
            createChannel(userId = 200L, platform = Platform.NAVER_CLIP),
        )

        every { videoRepository.findById(5L) } returns video
        every { videoRepository.findByContentHash(any()) } returns null
        every { videoRepository.update(any()) } returns video.copy(fileUrl = fileUrl)
        every { channelRepository.findByUserId(200L) } returns allChannels

        useCase.completeUpload(5L, fileUrl, "hash456")

        verify {
            eventPublisher.publishEvent(match<VideoTranscodingEvent> {
                it.platforms.size == 4 &&
                    it.platforms.containsAll(
                        listOf(Platform.YOUTUBE, Platform.TIKTOK, Platform.INSTAGRAM, Platform.NAVER_CLIP)
                    )
            })
        }
    }
}
