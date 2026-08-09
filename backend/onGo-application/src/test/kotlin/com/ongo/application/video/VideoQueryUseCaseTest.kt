package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.Visibility
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.TokenEncryptionPort
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
    private val storageService = mockk<StorageService>(relaxed = true)
    private val channelRepository = mockk<ChannelRepository>(relaxed = true)
    private val tokenEncryptionPort = mockk<TokenEncryptionPort>(relaxed = true)
    private val platformClientPort = mockk<PlatformClientPort>(relaxed = true)

    private lateinit var useCase: VideoQueryUseCase

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = VideoQueryUseCase(
            videoRepository, videoUploadRepository, videoPlatformMetaRepository,
            contentImageRepository, storageService,
            channelRepository, tokenEncryptionPort, platformClientPort,
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

    @Test
    fun `updateVideo persists platform-specific draft metadata without publishing`() {
        val video = createVideo()
        val savedUpload = VideoUpload(
            id = 41L,
            videoId = video.id!!,
            platform = Platform.YOUTUBE,
            status = UploadStatus.DRAFT,
        )
        every { videoRepository.findById(1L) } returns video
        every { videoRepository.update(any()) } returns video
        every { videoUploadRepository.findByVideoId(1L) } returns listOf(savedUpload)
        every { videoUploadRepository.save(any()) } returns savedUpload
        every { videoUploadRepository.update(any()) } returns savedUpload
        every { videoPlatformMetaRepository.findByVideoUploadId(41L) } returns null
        every { videoPlatformMetaRepository.save(any()) } answers { firstArg() }
        every { videoPlatformMetaRepository.findByVideoUploadIds(listOf(41L)) } returns emptyMap()
        every { videoUploadRepository.deleteEditableByVideoIdExceptTargets(1L, setOf(VideoUploadTarget(Platform.YOUTUBE, null))) } returns 0

        useCase.updateVideo(
            userId = 100L,
            videoId = 1L,
            title = "공통 제목",
            description = "공통 설명",
            tags = listOf("공통"),
            category = null,
            thumbnailIndex = null,
            platformDrafts = listOf(
                VideoPlatformDraft(
                    platform = Platform.YOUTUBE,
                    title = "유튜브 전용 제목",
                    description = "유튜브 전용 설명",
                    tags = listOf("유튜브"),
                    visibility = Visibility.PUBLIC,
                ),
            ),
        )

        verify {
            videoPlatformMetaRepository.save(match {
                it.videoUploadId == 41L &&
                    it.title == "유튜브 전용 제목" &&
                    it.description == "유튜브 전용 설명" &&
                    it.tags == listOf("유튜브")
            })
        }
        verify(exactly = 0) { videoUploadRepository.save(any()) }
    }

    @Test
    fun `updateVideo keeps two accounts of the same platform as independent drafts`() {
        val video = createVideo()
        val savedUploads = mutableListOf<VideoUpload>()
        val firstChannel = Channel(
            id = 101L,
            userId = 100L,
            platform = Platform.YOUTUBE,
            platformChannelId = "youtube-a",
            channelName = "브랜드 A",
            accessToken = EncryptedToken("token-a"),
        )
        val secondChannel = firstChannel.copy(
            id = 102L,
            platformChannelId = "youtube-b",
            channelName = "브랜드 B",
        )

        every { videoRepository.findById(1L) } returns video
        every { videoRepository.update(any()) } returns video
        every { videoUploadRepository.findByVideoId(1L) } answers { savedUploads.toList() }
        every { videoUploadRepository.save(any()) } answers {
            val requested = firstArg<VideoUpload>()
            val saved = requested.copy(id = requested.channelId)
            savedUploads += saved
            saved
        }
        every { channelRepository.findById(101L) } returns firstChannel
        every { channelRepository.findById(102L) } returns secondChannel
        every { videoPlatformMetaRepository.findByVideoUploadId(any()) } returns null
        every { videoPlatformMetaRepository.save(any()) } answers { firstArg() }
        every { videoPlatformMetaRepository.findByVideoUploadIds(any()) } returns emptyMap()
        every { videoUploadRepository.deleteEditableByVideoIdExceptTargets(1L, setOf(
            VideoUploadTarget(Platform.YOUTUBE, 101L),
            VideoUploadTarget(Platform.YOUTUBE, 102L),
        )) } returns 0

        useCase.updateVideo(
            userId = 100L,
            videoId = 1L,
            title = "공통 제목",
            description = "공통 설명",
            tags = listOf("공통"),
            category = null,
            thumbnailIndex = null,
            platformDrafts = listOf(
                VideoPlatformDraft(
                    platform = Platform.YOUTUBE,
                    channelId = 101L,
                    title = "브랜드 A 제목",
                    description = "A 설명",
                    tags = listOf("A"),
                    visibility = Visibility.PUBLIC,
                ),
                VideoPlatformDraft(
                    platform = Platform.YOUTUBE,
                    channelId = 102L,
                    title = "브랜드 B 제목",
                    description = "B 설명",
                    tags = listOf("B"),
                    visibility = Visibility.PUBLIC,
                ),
            ),
        )

        assert(savedUploads.map { it.channelId } == listOf(101L, 102L))
        verify(exactly = 2) { videoUploadRepository.save(any()) }
        verify {
            videoPlatformMetaRepository.save(match { it.videoUploadId == 101L && it.title == "브랜드 A 제목" })
            videoPlatformMetaRepository.save(match { it.videoUploadId == 102L && it.title == "브랜드 B 제목" })
        }
    }

    @Test
    fun `updateVideo rejects platform draft edits after publish has started`() {
        val video = createVideo(status = UploadStatus.UPLOADING)
        every { videoRepository.findById(1L) } returns video

        assertFailsWith<IllegalStateException> {
            useCase.updateVideo(
                userId = 100L,
                videoId = 1L,
                title = null,
                description = null,
                tags = null,
                category = null,
                thumbnailIndex = null,
                platformDrafts = emptyList(),
            )
        }
        verify(exactly = 0) { videoUploadRepository.findByVideoId(any()) }
    }

    // ---- deleteVideo tests ----

    @Test
    fun `deleteVideo should delete content images before video`() {
        val video = createVideo(id = 1L, userId = 100L)

        every { videoRepository.findById(1L) } returns video
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
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
