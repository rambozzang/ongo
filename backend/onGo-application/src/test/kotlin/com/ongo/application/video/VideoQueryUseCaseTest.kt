package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.MediaType
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.Visibility
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.video.*
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
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
    private val analyticsRepository = mockk<AnalyticsRepository>(relaxed = true)

    private lateinit var useCase: VideoQueryUseCase

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = VideoQueryUseCase(
            videoRepository, videoUploadRepository, videoPlatformMetaRepository,
            contentImageRepository, storageService,
            channelRepository, tokenEncryptionPort, platformClientPort,
            analyticsRepository,
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

    @Test
    fun `listVideos exposes stored view totals instead of a hardcoded zero`() {
        val video = createVideo(id = 1L, status = UploadStatus.PUBLISHED)
        val upload = VideoUpload(id = 41L, videoId = 1L, platform = Platform.YOUTUBE)
        every { videoRepository.findByUserId(100L, 0, 20, null) } returns listOf(video)
        every { videoRepository.countByUserId(100L, null) } returns 1L
        every { videoUploadRepository.findByVideoIds(listOf(1L)) } returns mapOf(1L to listOf(upload))
        every { analyticsRepository.findByVideoUploadIds(listOf(41L)) } returns listOf(
            AnalyticsDaily(videoUploadId = 41L, date = java.time.LocalDate.now(), views = 120),
            AnalyticsDaily(videoUploadId = 41L, date = java.time.LocalDate.now().minusDays(1), views = 30),
        )

        val result = useCase.listVideos(100L, 0, 20, null, null, null)

        assert(result.content.single().totalViews == 150L)
    }

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

    /*
     * 저장된 fileUrl 은 업로드 시점에 발급된 7일짜리 presigned URL 이다. 그대로 돌려주면
     * 7일 뒤 상세 화면의 프리뷰·다운로드가 죽고, 납품이 곧 제품인 쇼츠 파일럿에서는 고객이
     * 지난 결과물을 다시 받지 못한다. 조회할 때마다 새로 서명해 주되 DB 는 건드리지 않는다.
     */
    @Test
    fun `getVideoDetail returns a freshly signed url without writing it back`() {
        val expired = "https://storage/videos/1/video.mp4?X-Amz-Expires=1"
        val video = createVideo(fileUrl = expired)
        every { videoRepository.findById(1L) } returns video
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
        every { videoPlatformMetaRepository.findByVideoUploadIds(any()) } returns emptyMap()
        every { storageService.getFileUrl(1L, expired) } returns "https://storage/videos/1/video.mp4?fresh=1"

        val result = useCase.getVideoDetail(100L, 1L)

        assert(result.fileUrl == "https://storage/videos/1/video.mp4?fresh=1")
        verify(exactly = 1) { storageService.getFileUrl(1L, expired) }
        // 응답에만 담아야 한다. DB 를 갱신하면 조회가 쓰기 경로가 되고 만료 시각도 흩어진다.
        verify(exactly = 0) { videoRepository.update(any()) }
    }

    @Test
    fun `getVideoDetail keeps every other field while refreshing the url`() {
        val video = createVideo(fileUrl = "https://storage/videos/1/video.mp4")
        every { videoRepository.findById(1L) } returns video
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
        every { videoPlatformMetaRepository.findByVideoUploadIds(any()) } returns emptyMap()
        every { storageService.getFileUrl(any(), any()) } returns "https://storage/fresh.mp4"

        val result = useCase.getVideoDetail(100L, 1L)

        assert(result.id == video.id)
        assert(result.title == video.title)
        assert(result.description == video.description)
        assert(result.tags == video.tags)
        assert(result.fileSize == video.fileSizeBytes)
        assert(result.status == video.status)
        assert(result.mediaType == video.mediaType)
        assert(result.createdAt == video.createdAt)
    }

    /*
     * VideoStorageService.getFileUrl 은 저장 URL 을 오브젝트 키로 풀지 못하면 예외를 던진다.
     * 외부 remote URL·레거시 키·스토리지 일시 장애에서 상세 조회가 500 이 되면 안 되므로,
     * 재서명 실패는 저장된 값으로 조용히 되돌아간다.
     */
    @Test
    fun `getVideoDetail falls back to the stored url when re-signing throws`() {
        val stored = "https://cdn.example.com/external/video.mp4"
        val video = createVideo(fileUrl = stored)
        every { videoRepository.findById(1L) } returns video
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
        every { videoPlatformMetaRepository.findByVideoUploadIds(any()) } returns emptyMap()
        every { storageService.getFileUrl(1L, stored) } throws IllegalStateException("업로드된 파일을 찾을 수 없습니다")

        val result = useCase.getVideoDetail(100L, 1L)

        assert(result.fileUrl == stored)
        verify(exactly = 0) { videoRepository.update(any()) }
    }

    @Test
    fun `getVideoDetail does not touch storage when the video has no file`() {
        val video = createVideo(fileUrl = null)
        every { videoRepository.findById(1L) } returns video
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
        every { videoPlatformMetaRepository.findByVideoUploadIds(any()) } returns emptyMap()

        val result = useCase.getVideoDetail(100L, 1L)

        assert(result.fileUrl == null)
        verify(exactly = 0) { storageService.getFileUrl(any(), any()) }
    }

    @Test
    fun `getVideoDetail does not touch storage when the stored url is blank`() {
        val video = createVideo(fileUrl = "   ")
        every { videoRepository.findById(1L) } returns video
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
        every { videoPlatformMetaRepository.findByVideoUploadIds(any()) } returns emptyMap()

        val result = useCase.getVideoDetail(100L, 1L)

        assert(result.fileUrl == "   ")
        verify(exactly = 0) { storageService.getFileUrl(any(), any()) }
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
    fun `platforms without a separate description field can still save caption source text`() {
        val video = createVideo()
        val savedUpload = VideoUpload(
            id = 41L,
            videoId = video.id!!,
            platform = Platform.TIKTOK,
            status = UploadStatus.DRAFT,
        )
        every { videoRepository.findById(1L) } returns video
        every { videoRepository.update(any()) } returns video
        every { videoUploadRepository.findByVideoId(1L) } returns listOf(savedUpload)
        every { videoUploadRepository.update(any()) } returns savedUpload
        every { videoPlatformMetaRepository.findByVideoUploadId(41L) } returns null
        every { videoPlatformMetaRepository.save(any()) } answers { firstArg() }
        every { videoPlatformMetaRepository.findByVideoUploadIds(listOf(41L)) } returns emptyMap()
        every { videoUploadRepository.deleteEditableByVideoIdExceptTargets(1L, setOf(VideoUploadTarget(Platform.TIKTOK, null))) } returns 0

        useCase.updateVideo(
            userId = 100L,
            videoId = 1L,
            title = "틱톡 제목",
            description = "자막에서 만든 캡션 원문",
            tags = emptyList(),
            category = null,
            thumbnailIndex = null,
            platformDrafts = listOf(
                VideoPlatformDraft(
                    platform = Platform.TIKTOK,
                    title = "틱톡 제목",
                    description = "자막에서 만든 캡션 원문",
                    tags = emptyList(),
                    visibility = Visibility.PUBLIC,
                ),
            ),
        )

        verify {
            videoPlatformMetaRepository.save(match {
                it.videoUploadId == 41L && it.description == "자막에서 만든 캡션 원문"
            })
        }
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

    @Test
    fun `uploadContentImages records canonical URL for image posts`() {
        val imageVideo = createVideo(fileUrl = null).copy(mediaType = MediaType.IMAGE)
        val savedImage = ContentImage(
            id = 41L,
            videoId = imageVideo.id!!,
            imageUrl = "https://storage.test/content/1/cover.png",
            originalFilename = "cover.png",
            contentType = "image/png",
        )
        every { videoRepository.findById(1L) } returns imageVideo
        every { contentImageRepository.findByVideoId(1L) } returns emptyList()
        every { storageService.uploadFile(any(), any(), "image/png", 3L) } returns savedImage.imageUrl
        every { contentImageRepository.saveAll(any()) } returns listOf(savedImage)
        every { videoRepository.update(any()) } returns imageVideo.copy(fileUrl = savedImage.imageUrl)

        val result = useCase.uploadContentImages(
            100L,
            1L,
            listOf(MockMultipartFile("files", "cover.png", "image/png", byteArrayOf(1, 2, 3))),
        )

        assert(result.single().imageUrl == savedImage.imageUrl)
        verify { videoRepository.update(match { it.fileUrl == savedImage.imageUrl && it.mediaType == MediaType.IMAGE }) }
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
    fun `deleteVideo reports an external deletion failure but still removes local content`() {
        val video = createVideo(id = 1L, userId = 100L)
        val upload = VideoUpload(
            id = 41L,
            videoId = 1L,
            platform = Platform.YOUTUBE,
            channelId = 77L,
            platformVideoId = "youtube-video-1",
            status = UploadStatus.PUBLISHED,
        )
        val channel = Channel(
            id = 77L,
            userId = 100L,
            platform = Platform.YOUTUBE,
            platformChannelId = "channel-1",
            channelName = "테스트 채널",
            accessToken = EncryptedToken("encrypted-token"),
        )

        every { videoRepository.findById(1L) } returns video
        every { videoUploadRepository.findByVideoId(1L) } returns listOf(upload)
        every { channelRepository.findById(77L) } returns channel
        every { tokenEncryptionPort.decrypt(channel.accessToken) } returns com.ongo.domain.channel.PlainToken("access-token")
        every { platformClientPort.deleteVideo(Platform.YOUTUBE, "youtube-video-1", any()) } returns false
        every { contentImageRepository.deleteByVideoId(1L) } just runs
        every { videoRepository.delete(1L) } just runs

        val result = useCase.deleteVideo(100L, 1L)

        assert(result.externalFailures.single().platform == Platform.YOUTUBE)
        verify { videoRepository.delete(1L) }
        verify { contentImageRepository.deleteByVideoId(1L) }
    }

    @Test
    fun `deleteVideo reports storage cleanup failure instead of claiming a clean delete`() {
        val video = createVideo(id = 1L, userId = 100L)
        every { videoRepository.findById(1L) } returns video
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
        every { storageService.deleteFile(1L) } throws IllegalStateException("storage unavailable")
        every { contentImageRepository.deleteByVideoId(1L) } just runs
        every { videoRepository.delete(1L) } just runs

        val result = useCase.deleteVideo(100L, 1L)

        assert(result.storageDeletionFailed)
        verify { videoRepository.delete(1L) }
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
