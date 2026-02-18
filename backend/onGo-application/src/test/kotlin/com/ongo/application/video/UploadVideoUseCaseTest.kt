package com.ongo.application.video

import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.DuplicateResourceException
import com.ongo.common.exception.NotFoundException
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
    private val storageQuotaUseCase = mockk<StorageQuotaUseCase>(relaxed = true)
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private lateinit var useCase: UploadVideoUseCase

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = UploadVideoUseCase(
            videoRepository, subscriptionRepository, storageService, storageQuotaUseCase, eventPublisher,
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

    @Test
    fun `completeUpload should publish VideoPostProcessEvent`() {
        val video = createVideo(id = 1L, userId = 100L)
        val fileUrl = "https://storage.example.com/videos/1/video.mp4"

        every { videoRepository.findById(1L) } returns video
        every { videoRepository.findByContentHash(any()) } returns null
        every { videoRepository.update(any()) } returns video.copy(fileUrl = fileUrl)

        useCase.completeUpload(1L, fileUrl, "hash123")

        verify {
            eventPublisher.publishEvent(match<VideoPostProcessEvent> {
                it.videoId == 1L &&
                    it.userId == 100L &&
                    it.fileUrl == fileUrl
            })
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

        useCase.completeUpload(1L, fileUrl, "hash123")

        verify { videoRepository.update(any()) }
    }

    @Test
    fun `completeUpload should still update video when contentHash is null`() {
        val video = createVideo(id = 1L, userId = 100L)
        val fileUrl = "https://storage.example.com/videos/1/video.mp4"

        every { videoRepository.findById(1L) } returns video
        every { videoRepository.update(any()) } returns video.copy(fileUrl = fileUrl)

        useCase.completeUpload(1L, fileUrl, null)

        verify {
            videoRepository.update(match {
                it.fileUrl == fileUrl && it.contentHash == null && it.status == UploadStatus.DRAFT
            })
        }
        verify(exactly = 0) { videoRepository.findByContentHash(any()) }
    }
}
