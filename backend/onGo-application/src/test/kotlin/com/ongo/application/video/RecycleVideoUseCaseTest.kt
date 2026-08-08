package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RecycleVideoUseCaseTest {
    private val videoRepository = mockk<VideoRepository>()
    private val publishVideoUseCase = mockk<PublishVideoUseCase>()
    private val useCase = RecycleVideoUseCase(videoRepository, publishVideoUseCase)

    @Test
    fun `다른 사용자의 원본은 재게시할 수 없다`() {
        every { videoRepository.findById(10L) } returns Video(id = 10L, userId = 99L, title = "남의 영상", fileUrl = "https://storage/video.mp4")

        assertThrows(com.ongo.common.exception.ForbiddenException::class.java) {
            useCase.recycle(
                userId = 1L,
                sourceVideoId = 10L,
                title = "복사본",
                description = null,
                tags = emptyList(),
                category = null,
                platforms = listOf(RecyclePlatformConfig(Platform.YOUTUBE)),
            )
        }
        verify(exactly = 0) { videoRepository.save(any()) }
    }

    @Test
    fun `원본 미디어를 새 초안에 연결하고 독립 게시 흐름으로 넘긴다`() {
        val source = Video(
            id = 10L,
            userId = 1L,
            title = "원본",
            fileUrl = "https://storage/video.mp4",
            fileSizeBytes = 123L,
            thumbnailUrls = listOf("https://storage/thumb.jpg"),
        )
        val recycled = source.copy(id = 20L, title = "복사본", status = UploadStatus.DRAFT)
        every { videoRepository.findById(10L) } returns source
        every { videoRepository.save(any()) } returns recycled
        every { publishVideoUseCase.publishVideo(1L, 20L, any()) } returns PublishResult(
            videoId = 20L,
            uploads = listOf(PlatformUploadStatus(Platform.YOUTUBE, UploadStatus.UPLOADING)),
        )

        val result = useCase.recycle(
            userId = 1L,
            sourceVideoId = 10L,
            title = "복사본",
            description = "새 설명",
            tags = listOf("재게시"),
            category = "교육",
            platforms = listOf(RecyclePlatformConfig(Platform.YOUTUBE)),
        )

        assertEquals(20L, result.videoId)
        verify {
            videoRepository.save(match {
                it.title == "복사본" &&
                    it.fileUrl == source.fileUrl &&
                    it.thumbnailUrls == source.thumbnailUrls &&
                    it.status == UploadStatus.DRAFT
            })
            publishVideoUseCase.publishVideo(1L, 20L, match { it.single().platform == Platform.YOUTUBE })
        }
    }
}
