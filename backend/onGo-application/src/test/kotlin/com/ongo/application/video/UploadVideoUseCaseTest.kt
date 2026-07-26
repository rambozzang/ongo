package com.ongo.application.video

import com.ongo.common.enums.MediaType
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UploadVideoUseCaseTest {

    private val videoRepository = mockk<VideoRepository>()
    private val storageService = mockk<StorageService>(relaxed = true)

    private lateinit var useCase: UploadVideoUseCase

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = UploadVideoUseCase(videoRepository, storageService)
    }

    @Test
    fun `createVideo should save video with DRAFT status`() {
        val savedVideo = Video(
            id = 1L,
            userId = 100L,
            title = "테스트 영상",
            status = UploadStatus.DRAFT,
            mediaType = MediaType.VIDEO,
        )
        every { videoRepository.save(any()) } returns savedVideo

        val result = useCase.createVideo(
            userId = 100L,
            title = "테스트 영상",
        )

        assertEquals(1L, result.id)
        assertEquals(UploadStatus.DRAFT, result.status)
        verify { videoRepository.save(match { it.status == UploadStatus.DRAFT && it.title == "테스트 영상" }) }
    }
}
