package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.MediaType
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.Visibility
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.StorageQuotaExceededException
import com.ongo.application.storage.StorageQuotaUseCase
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
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
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
    private val storageQuotaUseCase = mockk<StorageQuotaUseCase>(relaxed = true)

    private lateinit var useCase: VideoQueryUseCase

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = VideoQueryUseCase(
            videoRepository, videoUploadRepository, videoPlatformMetaRepository,
            contentImageRepository, storageService,
            channelRepository, tokenEncryptionPort, platformClientPort,
            analyticsRepository, storageQuotaUseCase,
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

    /* ── 이미지 게시: 쿼터 ──────────────────────────────────────────── */

    /**
     * **이미지도 저장 공간을 쓴다.** 영상·에셋은 쿼터를 검사하는데 이미지만 빠져 있었다.
     *
     * 신고치가 아니라 `MultipartFile.size` — 서버가 실제로 받은 바이트 — 로 검사한다.
     * 여러 장을 한 번에 올리므로 합계로 봐야 한다. 장당으로 나눠 보면 한도 직전에서
     * 각각은 통과하고 합쳐서 넘긴다.
     */
    @Test
    fun `이미지 업로드는 실제 파일 크기 합계로 쿼터를 검사한다`() {
        val imageVideo = createVideo(fileUrl = null).copy(mediaType = MediaType.IMAGE)
        every { videoRepository.findById(1L) } returns imageVideo
        every { contentImageRepository.findByVideoId(1L) } returns emptyList()
        every { storageService.uploadFile(any(), any(), any(), any()) } returns "https://storage.test/x.png"
        every { contentImageRepository.saveAll(any()) } returns emptyList()

        useCase.uploadContentImages(
            100L,
            1L,
            listOf(
                MockMultipartFile("files", "a.png", "image/png", ByteArray(1_000)),
                MockMultipartFile("files", "b.png", "image/png", ByteArray(2_500)),
            ),
        )

        verify(exactly = 1) { storageQuotaUseCase.checkQuota(100L, 3_500L, any()) }
    }

    /** 한도를 넘기면 **스토리지에 한 바이트도 올리지 않는다.** 올린 뒤 거절하면 고아가 남는다. */
    @Test
    fun `쿼터를 넘기면 스토리지에 올리지 않는다`() {
        val imageVideo = createVideo(fileUrl = null).copy(mediaType = MediaType.IMAGE)
        every { videoRepository.findById(1L) } returns imageVideo
        every { contentImageRepository.findByVideoId(1L) } returns emptyList()
        every { storageQuotaUseCase.checkQuota(any(), any(), any()) } throws
            StorageQuotaExceededException(limitBytes = 1_000, usedBytes = 900, requiredBytes = 3_500)

        assertFailsWith<StorageQuotaExceededException> {
            useCase.uploadContentImages(
                100L,
                1L,
                listOf(MockMultipartFile("files", "a.png", "image/png", ByteArray(3_500))),
            )
        }

        verify(exactly = 0) { storageService.uploadFile(any(), any(), any(), any()) }
        verify(exactly = 0) { contentImageRepository.saveAll(any()) }
    }

    /* ── 이미지 게시: 보상 ──────────────────────────────────────────── */

    /**
     * **중간에 실패하면 이미 올린 것을 되돌린다.**
     *
     * 트랜잭션은 DB 행만 되돌린다. 스토리지는 트랜잭션 밖이라 앞 장들이 그대로 남고,
     * 그 객체를 가리키는 행은 롤백돼 사라진다 — 아무도 못 찾고 과금만 되는 고아다.
     */
    @Test
    fun `업로드 도중 실패하면 이미 올린 객체를 지운다`() {
        val imageVideo = createVideo(fileUrl = null).copy(mediaType = MediaType.IMAGE)
        every { videoRepository.findById(1L) } returns imageVideo
        every { contentImageRepository.findByVideoId(1L) } returns emptyList()
        val keys = mutableListOf<String>()
        every { storageService.uploadFile(capture(keys), any(), any(), any()) } answers {
            if (keys.size >= 2) throw IllegalStateException("스토리지 장애")
            "https://storage.test/${keys.last()}"
        }

        assertFailsWith<IllegalStateException> {
            useCase.uploadContentImages(
                100L,
                1L,
                listOf(
                    MockMultipartFile("files", "a.png", "image/png", ByteArray(10)),
                    MockMultipartFile("files", "b.png", "image/png", ByteArray(10)),
                ),
            )
        }

        // 성공한 앞 장뿐 아니라 **던진 장의 키도** 지워야 한다 — 객체가 이미 만들어졌을 수 있다.
        verify(exactly = 1) { storageService.deleteFileByKey(keys[0]) }
        verify(exactly = 1) { storageService.deleteFileByKey(keys[1]) }
    }

    /**
     * **키는 올리기 전에 등록한다.**
     *
     * `uploadFile` 은 객체를 만든 뒤 URL 을 만들어 돌려준다. 그 후처리에서 던지면
     * **객체는 이미 스토리지에 있는데** 반환값이 없어 키가 보상 목록에 들어가지 못했다.
     * 아무도 못 찾고 과금만 되는 고아다 — 실패를 되돌린다고 믿는 코드가 남기는 고아라
     * 더 나쁘다.
     *
     * 만들어지지 않은 객체의 키를 미리 등록해도 손해는 없다. 없는 키를 지우는 것은
     * S3·MinIO 모두 무해한 no-op 이고, 정리는 어차피 건별로 `runCatching` 이다.
     */
    @Test
    fun `업로드 호출 자체가 던져도 그 키를 정리한다`() {
        val imageVideo = createVideo(fileUrl = null).copy(mediaType = MediaType.IMAGE)
        every { videoRepository.findById(1L) } returns imageVideo
        every { contentImageRepository.findByVideoId(1L) } returns emptyList()
        val keys = mutableListOf<String>()
        // 객체는 만들어졌는데 URL 생성에서 터진 상황이다 — 반환값이 없다.
        every { storageService.uploadFile(capture(keys), any(), any(), any()) } throws
            IllegalStateException("URL 생성 실패")

        assertFailsWith<IllegalStateException> {
            useCase.uploadContentImages(
                100L,
                1L,
                listOf(
                    MockMultipartFile("files", "a.png", "image/png", ByteArray(10)),
                    MockMultipartFile("files", "b.png", "image/png", ByteArray(10)),
                ),
            )
        }

        assert(keys.size == 1) { "첫 장이 실패했는데 다음 장을 계속 올렸다: $keys" }
        verify(exactly = 1) { storageService.deleteFileByKey(keys.single()) }
        // 되돌릴 근거 없는 행이 남으면 안 된다.
        verify(exactly = 0) { contentImageRepository.saveAll(any()) }
    }

    /** 행 저장이 실패해도 마찬가지다 — 올린 객체 전부를 되돌린다. */
    @Test
    fun `행 저장이 실패하면 올린 객체를 모두 지운다`() {
        val imageVideo = createVideo(fileUrl = null).copy(mediaType = MediaType.IMAGE)
        every { videoRepository.findById(1L) } returns imageVideo
        every { contentImageRepository.findByVideoId(1L) } returns emptyList()
        every { storageService.uploadFile(any(), any(), any(), any()) } returns "https://storage.test/x.png"
        every { contentImageRepository.saveAll(any()) } throws RuntimeException("DB 오류")

        assertFailsWith<RuntimeException> {
            useCase.uploadContentImages(
                100L,
                1L,
                listOf(
                    MockMultipartFile("files", "a.png", "image/png", ByteArray(10)),
                    MockMultipartFile("files", "b.png", "image/png", ByteArray(10)),
                ),
            )
        }

        verify(exactly = 2) { storageService.deleteFileByKey(any()) }
    }

    /**
     * **커밋이 실패해도 되돌린다.**
     *
     * 이 메서드가 예외 없이 끝나도 커밋 자체는 실패할 수 있다. 그러면 행은 사라지는데
     * 객체는 남는다 — `try/catch` 로는 닿지 않는 창이라 트랜잭션 동기화가 필요하다.
     *
     * 실제 DB 없이 그 창을 재현한다: 동기화를 열어 두고, 등록된 콜백을 롤백 상태로 직접
     * 돌린다. 커밋 실패 시 스프링이 하는 일과 같다.
     */
    @Test
    fun `커밋이 실패하면 올린 객체를 되돌린다`() {
        val imageVideo = createVideo(fileUrl = null).copy(mediaType = MediaType.IMAGE)
        every { videoRepository.findById(1L) } returns imageVideo
        every { contentImageRepository.findByVideoId(1L) } returns emptyList()
        val keys = mutableListOf<String>()
        every { storageService.uploadFile(capture(keys), any(), any(), any()) } returns "https://storage.test/x.png"
        every { contentImageRepository.saveAll(any()) } returns emptyList()

        TransactionSynchronizationManager.initSynchronization()
        try {
            useCase.uploadContentImages(
                100L,
                1L,
                listOf(MockMultipartFile("files", "a.png", "image/png", ByteArray(10))),
            )
            // 여기까지는 정상이다. 정리를 미리 하면 안 된다.
            verify(exactly = 0) { storageService.deleteFileByKey(any()) }

            rollbackRegisteredSynchronizations()

            verify(exactly = 1) { storageService.deleteFileByKey(keys.single()) }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    /** 커밋되면 아무것도 지우지 않는다 — 되돌릴 일이 없는데 지우면 방금 올린 파일이 사라진다. */
    @Test
    fun `커밋되면 올린 객체를 지우지 않는다`() {
        val imageVideo = createVideo(fileUrl = null).copy(mediaType = MediaType.IMAGE)
        every { videoRepository.findById(1L) } returns imageVideo
        every { contentImageRepository.findByVideoId(1L) } returns emptyList()
        every { storageService.uploadFile(any(), any(), any(), any()) } returns "https://storage.test/x.png"
        every { contentImageRepository.saveAll(any()) } returns emptyList()

        TransactionSynchronizationManager.initSynchronization()
        try {
            useCase.uploadContentImages(
                100L,
                1L,
                listOf(MockMultipartFile("files", "a.png", "image/png", ByteArray(10))),
            )
            TransactionSynchronizationManager.getSynchronizations()
                .forEach { it.afterCompletion(TransactionSynchronization.STATUS_COMMITTED) }

            verify(exactly = 0) { storageService.deleteFileByKey(any()) }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    /**
     * **두 보상 경로가 겹쳐도 한 번만 지운다.**
     *
     * 메서드 안에서 던지면 `catch` 가 지우고, 그 뒤 트랜잭션이 롤백되면 `afterCompletion`
     * 도 돈다. 목록을 비우지 않으면 같은 키를 두 번 지운다 — 삭제 자체는 무해하지만
     * "지웠다" 로그가 두 배로 남아 고아를 추적할 때 어느 것이 실제인지 알 수 없게 된다.
     */
    @Test
    fun `롤백 보상은 이미 지운 키를 다시 지우지 않는다`() {
        val imageVideo = createVideo(fileUrl = null).copy(mediaType = MediaType.IMAGE)
        every { videoRepository.findById(1L) } returns imageVideo
        every { contentImageRepository.findByVideoId(1L) } returns emptyList()
        val keys = mutableListOf<String>()
        every { storageService.uploadFile(capture(keys), any(), any(), any()) } answers {
            if (keys.size >= 2) throw IllegalStateException("스토리지 장애")
            "https://storage.test/${keys.last()}"
        }

        TransactionSynchronizationManager.initSynchronization()
        try {
            assertFailsWith<IllegalStateException> {
                useCase.uploadContentImages(
                    100L,
                    1L,
                    listOf(
                        MockMultipartFile("files", "a.png", "image/png", ByteArray(10)),
                        MockMultipartFile("files", "b.png", "image/png", ByteArray(10)),
                    ),
                )
            }
            rollbackRegisteredSynchronizations()

            verify(exactly = 1) { storageService.deleteFileByKey(keys[0]) }
            verify(exactly = 1) { storageService.deleteFileByKey(keys[1]) }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    /** 커밋 실패로 스프링이 등록된 콜백을 돌리는 것과 같은 동작. */
    private fun rollbackRegisteredSynchronizations() {
        TransactionSynchronizationManager.getSynchronizations()
            .forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }
    }

    /**
     * **서버가 할당한 키를 행에 남긴다.**
     *
     * 탈퇴 정리는 사용자별 접두사가 없어 키를 하나씩 알아야 한다. URL 에서 되짚으면
     * 서명·경로 형식 차이로 빗나가고, 빗나간 삭제는 남의 파일을 지운다.
     */
    @Test
    fun `이미지 업로드는 실제 저장 키를 함께 남긴다`() {
        val imageVideo = createVideo(fileUrl = null).copy(mediaType = MediaType.IMAGE)
        every { videoRepository.findById(1L) } returns imageVideo
        every { contentImageRepository.findByVideoId(1L) } returns emptyList()
        val uploadedKey = slot<String>()
        every { storageService.uploadFile(capture(uploadedKey), any(), any(), any()) } returns "https://storage.test/x.png"
        val rows = slot<List<ContentImage>>()
        every { contentImageRepository.saveAll(capture(rows)) } returns emptyList()

        useCase.uploadContentImages(
            100L,
            1L,
            listOf(MockMultipartFile("files", "a.png", "image/png", ByteArray(10))),
        )

        val persisted = rows.captured.single().storageObjectKey
        assert(persisted == uploadedKey.captured) { "올린 키와 다른 값을 저장했다: $persisted" }
        assert(persisted!!.startsWith("content/1/images/")) { "키 형식이 바뀌었다: $persisted" }
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

    /**
     * **영상 삭제는 이미지 객체까지 지운다.**
     *
     * 이미지는 `content/{videoId}/` 접두사에 있는데 영상 삭제는 `videos/{videoId}/` 만
     * 지웠다. 행은 사라지고 객체만 남아 아무도 찾을 수 없는 고아가 됐다.
     */
    @Test
    fun `영상 삭제는 이미지 객체도 정리한다`() {
        val video = createVideo(id = 1L, userId = 100L)
        every { videoRepository.findById(1L) } returns video
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
        every { contentImageRepository.deleteByVideoId(1L) } just runs
        every { videoRepository.delete(1L) } just runs

        useCase.deleteVideo(100L, 1L)

        verify(exactly = 1) { storageService.deleteContentImages(1L) }
    }

    /** 이미지 정리 실패를 성공으로 숨기지 않는다 — 사용자는 남은 파일을 알아야 한다. */
    @Test
    fun `이미지 정리 실패도 정리 실패로 보고한다`() {
        val video = createVideo(id = 1L, userId = 100L)
        every { videoRepository.findById(1L) } returns video
        every { videoUploadRepository.findByVideoId(1L) } returns emptyList()
        every { storageService.deleteContentImages(1L) } throws IllegalStateException("storage unavailable")
        every { contentImageRepository.deleteByVideoId(1L) } just runs
        every { videoRepository.delete(1L) } just runs

        val result = useCase.deleteVideo(100L, 1L)

        assert(result.storageDeletionFailed) { "이미지를 못 지웠는데 깨끗이 지웠다고 보고했다" }
        verify { videoRepository.delete(1L) }
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
