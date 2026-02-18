package com.ongo.application.video

import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.video.*
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class VideoQueryUseCaseTest {

    private val videoRepository = mockk<VideoRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val videoPlatformMetaRepository = mockk<VideoPlatformMetaRepository>()
    private val contentImageRepository = mockk<ContentImageRepository>(relaxed = true)
    private val mediaInfoRepository = mockk<VideoMediaInfoRepository>(relaxed = true)
    private val subtitleRepository = mockk<VideoSubtitleRepository>(relaxed = true)
    private val storageService = mockk<StorageService>(relaxed = true)

    private lateinit var useCase: VideoQueryUseCase

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = VideoQueryUseCase(
            videoRepository, videoUploadRepository, videoPlatformMetaRepository,
            contentImageRepository, mediaInfoRepository,
            subtitleRepository, storageService,
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

    // ---- getVideoDetail tests ----

    @Test
    fun `getVideoDetail should return detail without variants`() {
        val video = createVideo()

        every { videoRepository.findById(1L) } returns video
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
        every { videoPlatformMetaRepository.findByVideoUploadIds(any()) } returns emptyMap()

        val result = useCase.getVideoDetail(100L, 1L)

        assert(result.uploads.isEmpty())
        assert(result.id == 1L)
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

    // ---- deleteVideo tests ----

    @Test
    fun `deleteVideo should delete content images before video`() {
        val video = createVideo(id = 1L, userId = 100L)

        every { videoRepository.findById(1L) } returns video
        every { contentImageRepository.deleteByVideoId(1L) } just runs
        every { videoRepository.delete(1L) } just runs

        useCase.deleteVideo(100L, 1L)

        verifyOrder {
            contentImageRepository.deleteByVideoId(1L)
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
