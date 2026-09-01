package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.MediaType
import com.ongo.application.platform.PlatformConfigurationPort
import com.ongo.application.platform.PlatformConfigurationStatus
import com.ongo.common.exception.AccountFrozenException
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoPlatformMetaRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.time.LocalDateTime

class PublishVideoUseCaseTest {
    private val videoRepository = mockk<VideoRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>(relaxed = true)
    private val videoPlatformMetaRepository = mockk<VideoPlatformMetaRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>()
    private val channelRepository = mockk<ChannelRepository>()
    private val videoUploadPoller = mockk<VideoUploadPoller>(relaxed = true)
    private val userWriteGuard = mockk<UserWriteGuard>(relaxed = true)
    private val scheduleRepository = mockk<ScheduleRepository>(relaxed = true)

    @Test
    fun `cannot publish a video owned by another user`() {
        every { videoRepository.findById(900L) } returns Video(
            id = 900L,
            userId = 7L,
            title = "다른 사용자의 영상",
            fileUrl = "https://storage.test/original.mp4",
            status = UploadStatus.DRAFT,
        )

        assertFailsWith<ForbiddenException> {
            useCase().publishVideo(
                userId = 42L,
                videoId = 900L,
                configs = listOf(
                    PlatformUploadConfig(
                        platform = Platform.YOUTUBE,
                        videoUploadId = 0L,
                        title = "게시 시도",
                        description = null,
                        tags = emptyList(),
                        visibility = Visibility.PUBLIC,
                        thumbnailUrl = null,
                        scheduledAt = null,
                    ),
                ),
            )
        }
        verify(exactly = 0) { videoUploadRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `frozen account cannot start a publish write`() {
        every { userWriteGuard.requireWritable(42L) } throws AccountFrozenException()

        assertFailsWith<AccountFrozenException> {
            useCase().publishVideo(
                userId = 42L,
                videoId = 900L,
                configs = listOf(
                    PlatformUploadConfig(
                        platform = Platform.YOUTUBE,
                        videoUploadId = 0L,
                        title = "게시 시도",
                        description = null,
                        tags = emptyList(),
                        visibility = Visibility.PUBLIC,
                        thumbnailUrl = null,
                        scheduledAt = null,
                    ),
                ),
            )
        }

        verify(exactly = 0) { videoRepository.findById(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `이미지 영상은 기본 config여도 게시 이벤트를 이미지 타입으로 정규화한다`() {
        val videoId = 901L
        val video = Video(
            id = videoId,
            userId = 42L,
            title = "이미지 게시",
            fileUrl = "https://storage.test/image.jpg",
            mediaType = MediaType.IMAGE,
            status = UploadStatus.DRAFT,
        )
        val event = slot<VideoPublishEvent>()
        every { videoRepository.findById(videoId) } returns video
        every { videoRepository.claimForPublish(42L, videoId) } returns true
        every { channelRepository.findByUserIdAndPlatform(42L, Platform.INSTAGRAM) } returns Channel(
            id = 101L,
            userId = 42L,
            platform = Platform.INSTAGRAM,
            platformChannelId = "ig-user",
            channelName = "Instagram",
            accessToken = EncryptedToken("token"),
        )
        every { videoUploadRepository.save(any()) } returns VideoUpload(
            id = 1L,
            videoId = videoId,
            platform = Platform.INSTAGRAM,
            status = UploadStatus.UPLOADING,
        )
        every { videoPlatformMetaRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publishEvent(capture(event)) } just Runs

        useCase().publishVideo(
            userId = 42L,
            videoId = videoId,
            configs = listOf(
                PlatformUploadConfig(
                    platform = Platform.INSTAGRAM,
                    videoUploadId = 0L,
                    title = "이미지 게시",
                    description = "설명",
                    tags = emptyList(),
                    visibility = Visibility.PUBLIC,
                    thumbnailUrl = null,
                    scheduledAt = null,
                ),
            ),
        )

        assertEquals(MediaType.IMAGE, event.captured.platformConfigs.single().mediaType)
    }

    @Test
    fun `publishes one durable upload row for every enabled video channel`() {
        val userId = 42L
        val videoId = 900L
        val platforms = listOf(
            Platform.YOUTUBE,
            Platform.TIKTOK,
            Platform.INSTAGRAM,
            Platform.THREADS,
            Platform.FACEBOOK,
            Platform.PINTEREST,
            Platform.LINKEDIN,
            Platform.WORDPRESS,
            Platform.DAILYMOTION,
            Platform.VIMEO,
            Platform.TUMBLR,
        )
        val video = Video(
            id = videoId,
            userId = userId,
            title = "원본 영상",
            fileUrl = "https://storage.test/original.mp4",
            status = UploadStatus.DRAFT,
        )
        val event = slot<VideoPublishEvent>()
        var nextUploadId = 1L

        every { videoRepository.findById(videoId) } returns video
        every { videoRepository.claimForPublish(userId, videoId) } returns true
        every { channelRepository.findByUserIdAndPlatform(userId, any()) } answers {
            Channel(
                id = 100L,
                userId = userId,
                platform = secondArg(),
                platformChannelId = "channel-${secondArg<Platform>()}",
                channelName = "테스트 채널",
                accessToken = EncryptedToken("encrypted-token"),
            )
        }
        every { videoUploadRepository.save(any()) } answers {
            firstArg<VideoUpload>().copy(id = nextUploadId++)
        }
        every { videoPlatformMetaRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publishEvent(capture(event)) } just Runs

        val useCase = PublishVideoUseCase(
            videoRepository = videoRepository,
            videoUploadRepository = videoUploadRepository,
            videoPlatformMetaRepository = videoPlatformMetaRepository,
            eventPublisher = eventPublisher,
            channelRepository = channelRepository,
            videoUploadPoller = videoUploadPoller,
            userWriteGuard = userWriteGuard,
            scheduleRepository = scheduleRepository,
            platformConfigurationPort = allPlatformsConfigured,
        )

        val result = useCase.publishVideo(
            userId = userId,
            videoId = videoId,
            configs = platforms.map { platform ->
                PlatformUploadConfig(
                    platform = platform,
                    videoUploadId = 0L,
                    title = "플랫폼별 제목",
                    description = "플랫폼별 설명",
                    tags = emptyList(),
                    visibility = Visibility.PUBLIC,
                    thumbnailUrl = null,
                    scheduledAt = null,
                )
            },
        )

        assertEquals(platforms, result.uploads.map { it.platform })
        assertEquals(platforms, event.captured.platformConfigs.map { it.platform })
        assertEquals(platforms.size, event.captured.platformConfigs.map { it.videoUploadId }.distinct().size)
        verify(exactly = platforms.size) { videoUploadRepository.save(any()) }
        verify(exactly = platforms.size) { videoPlatformMetaRepository.save(any()) }
    }

    @Test
    fun `publishes two durable rows when the same platform has two selected accounts`() {
        val userId = 42L
        val videoId = 902L
        val video = Video(
            id = videoId,
            userId = userId,
            title = "멀티 계정 영상",
            fileUrl = "https://storage.test/multi-account.mp4",
            status = UploadStatus.DRAFT,
        )
        val event = slot<VideoPublishEvent>()
        var nextUploadId = 700L
        every { videoRepository.findById(videoId) } returns video
        every { videoRepository.claimForPublish(userId, videoId) } returns true
        every { channelRepository.findById(101L) } returns Channel(
            id = 101L, userId = userId, platform = Platform.INSTAGRAM,
            platformChannelId = "creator-a", channelName = "브랜드 A",
            accessToken = EncryptedToken("token-a"),
        )
        every { channelRepository.findById(102L) } returns Channel(
            id = 102L, userId = userId, platform = Platform.INSTAGRAM,
            platformChannelId = "creator-b", channelName = "브랜드 B",
            accessToken = EncryptedToken("token-b"),
        )
        every { videoUploadRepository.findByVideoIdAndChannelId(videoId, any()) } returns null
        every { videoUploadRepository.save(any()) } answers {
            firstArg<VideoUpload>().copy(id = nextUploadId++)
        }
        every { videoPlatformMetaRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publishEvent(capture(event)) } just Runs

        val result = useCase().publishVideo(
            userId = userId,
            videoId = videoId,
            configs = listOf(101L to "브랜드 A", 102L to "브랜드 B").map { (channelId, _) ->
                PlatformUploadConfig(
                    platform = Platform.INSTAGRAM,
                    videoUploadId = 0L,
                    channelId = channelId,
                    title = "계정별 제목",
                    description = "계정별 설명",
                    tags = listOf("ongo"),
                    visibility = Visibility.PUBLIC,
                    thumbnailUrl = null,
                    scheduledAt = null,
                )
            },
        )

        assertEquals(listOf(Platform.INSTAGRAM, Platform.INSTAGRAM), result.uploads.map { it.platform })
        assertEquals(listOf(101L, 102L), event.captured.platformConfigs.map { it.channelId })
        verify(exactly = 2) { videoUploadRepository.save(any()) }
    }

    @Test
    fun `selected channel does not fall back to another upload row on the same platform`() {
        val userId = 42L
        val videoId = 903L
        val otherAccountUpload = VideoUpload(
            id = 799L,
            videoId = videoId,
            platform = Platform.YOUTUBE,
            channelId = 201L,
            status = UploadStatus.PUBLISHED,
        )
        val saved = slot<VideoUpload>()
        val event = slot<VideoPublishEvent>()

        every { videoRepository.findById(videoId) } returns Video(
            id = videoId,
            userId = userId,
            title = "계정 선택 영상",
            fileUrl = "https://storage.test/account.mp4",
            status = UploadStatus.DRAFT,
        )
        every { videoRepository.claimForPublish(userId, videoId) } returns true
        every { channelRepository.findById(202L) } returns Channel(
            id = 202L,
            userId = userId,
            platform = Platform.YOUTUBE,
            platformChannelId = "selected-account",
            channelName = "선택 계정",
            accessToken = EncryptedToken("selected-token"),
        )
        every { videoUploadRepository.findByVideoIdAndChannelId(videoId, 202L) } returns null
        every { videoUploadRepository.findByVideoIdAndPlatform(videoId, Platform.YOUTUBE) } returns otherAccountUpload
        every { videoUploadRepository.save(capture(saved)) } answers { firstArg<VideoUpload>().copy(id = 800L) }
        every { videoPlatformMetaRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publishEvent(capture(event)) } just Runs

        val result = useCase().publishVideo(
            userId = userId,
            videoId = videoId,
            configs = listOf(
                PlatformUploadConfig(
                    platform = Platform.YOUTUBE,
                    channelId = 202L,
                    videoUploadId = 0L,
                    title = "선택 계정 제목",
                    description = "선택 계정 설명",
                    tags = emptyList(),
                    visibility = Visibility.PUBLIC,
                    thumbnailUrl = null,
                    scheduledAt = null,
                ),
            ),
        )

        assertEquals(202L, saved.captured.channelId)
        assertEquals(202L, event.captured.platformConfigs.single().channelId)
        assertEquals(800L, event.captured.platformConfigs.single().videoUploadId)
        assertEquals(Platform.YOUTUBE, result.uploads.single().platform)
        verify(exactly = 0) { videoUploadRepository.findByVideoIdAndPlatform(videoId, Platform.YOUTUBE) }
    }

    @Test
    fun `active upload for the selected channel is rejected before claiming the video`() {
        val userId = 42L
        val videoId = 904L
        every { videoRepository.findById(videoId) } returns Video(
            id = videoId,
            userId = userId,
            title = "이미 게시된 영상",
            fileUrl = "https://storage.test/already-published.mp4",
            status = UploadStatus.DRAFT,
        )
        every { channelRepository.findById(202L) } returns Channel(
            id = 202L,
            userId = userId,
            platform = Platform.YOUTUBE,
            platformChannelId = "selected-account",
            channelName = "선택 계정",
            accessToken = EncryptedToken("selected-token"),
        )
        every { videoUploadRepository.findByVideoIdAndChannelId(videoId, 202L) } returns VideoUpload(
            id = 799L,
            videoId = videoId,
            platform = Platform.YOUTUBE,
            channelId = 202L,
            status = UploadStatus.PUBLISHED,
            platformUrl = "https://youtube.com/watch?v=already-published",
        )

        assertFailsWith<IllegalArgumentException> {
            useCase().publishVideo(
                userId = userId,
                videoId = videoId,
                configs = listOf(
                    PlatformUploadConfig(
                        platform = Platform.YOUTUBE,
                        channelId = 202L,
                        videoUploadId = 0L,
                        title = "중복 제목",
                        description = "중복 설명",
                        tags = emptyList(),
                        visibility = Visibility.PUBLIC,
                        thumbnailUrl = null,
                        scheduledAt = null,
                    ),
                ),
            )
        }

        verify(exactly = 0) { videoRepository.claimForPublish(any(), any()) }
        verify(exactly = 0) { videoUploadRepository.save(any()) }
    }

    @Test
    fun `scheduled publish creates a calendar schedule linked to its durable upload`() {
        val videoId = 905L
        val scheduledAt = LocalDateTime.now().plusHours(2)
        every { videoRepository.findById(videoId) } returns Video(
            id = videoId,
            userId = 42L,
            title = "예약 원본",
            fileUrl = "https://storage.test/scheduled.mp4",
            status = UploadStatus.DRAFT,
        )
        every { videoRepository.claimForPublish(42L, videoId) } returns true
        every { channelRepository.findByUserIdAndPlatform(42L, Platform.YOUTUBE) } returns Channel(
            id = 1L,
            userId = 42L,
            platform = Platform.YOUTUBE,
            platformChannelId = "youtube-channel",
            channelName = "테스트 채널",
            accessToken = EncryptedToken("encrypted-token"),
        )
        every { videoUploadRepository.save(any()) } returns VideoUpload(
            id = 501L,
            videoId = videoId,
            platform = Platform.YOUTUBE,
            status = UploadStatus.UPLOADING,
            scheduledAt = scheduledAt,
        )
        every { videoPlatformMetaRepository.save(any()) } answers { firstArg() }
        every { scheduleRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publishEvent(any<VideoPublishEvent>()) } just Runs

        useCase().publishVideo(
            userId = 42L,
            videoId = videoId,
            configs = listOf(
                PlatformUploadConfig(
                    platform = Platform.YOUTUBE,
                    videoUploadId = 0L,
                    title = "예약 제목",
                    description = "설명",
                    tags = emptyList(),
                    visibility = Visibility.PUBLIC,
                    thumbnailUrl = null,
                    scheduledAt = scheduledAt,
                ),
            ),
        )

        verify {
            scheduleRepository.save(match {
                it.videoId == videoId &&
                    it.status == com.ongo.common.enums.ScheduleStatus.SCHEDULED &&
                    it.scheduledAt == scheduledAt &&
                    it.platforms[Platform.YOUTUBE.name] != null
            })
        }
    }

    @Test
    fun `publishing a saved platform draft reuses its durable upload row`() {
        val videoId = 906L
        val draftUpload = VideoUpload(
            id = 601L,
            videoId = videoId,
            platform = Platform.YOUTUBE,
            status = UploadStatus.DRAFT,
        )
        every { videoRepository.findById(videoId) } returns Video(
            id = videoId,
            userId = 42L,
            title = "저장된 초안",
            fileUrl = "https://storage.test/draft.mp4",
            status = UploadStatus.DRAFT,
        )
        every { videoRepository.claimForPublish(42L, videoId) } returns true
        every { channelRepository.findByUserIdAndPlatform(42L, Platform.YOUTUBE) } returns Channel(
            id = 1L,
            userId = 42L,
            platform = Platform.YOUTUBE,
            platformChannelId = "youtube-channel",
            channelName = "테스트 채널",
            accessToken = EncryptedToken("encrypted-token"),
        )
        every { videoUploadRepository.findByVideoIdAndPlatform(videoId, Platform.YOUTUBE) } returns draftUpload
        every { videoUploadRepository.update(any()) } answers { firstArg() }
        every { videoPlatformMetaRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publishEvent(any<VideoPublishEvent>()) } just Runs

        useCase().publishVideo(
            userId = 42L,
            videoId = videoId,
            configs = listOf(
                PlatformUploadConfig(
                    platform = Platform.YOUTUBE,
                    videoUploadId = 0L,
                    title = "초안 제목",
                    description = "초안 설명",
                    tags = listOf("초안"),
                    visibility = Visibility.PUBLIC,
                    thumbnailUrl = null,
                    scheduledAt = null,
                ),
            ),
        )

        verify(exactly = 1) { videoUploadRepository.update(match { it.id == 601L && it.status == UploadStatus.UPLOADING }) }
        verify(exactly = 0) { videoUploadRepository.save(any()) }
    }

    @Test
    fun `concurrent publish reservation is rejected before creating platform rows`() {
        val userId = 42L
        val videoId = 900L
        every { videoRepository.findById(videoId) } returns Video(
            id = videoId,
            userId = userId,
            title = "원본 영상",
            fileUrl = "https://storage.test/original.mp4",
            status = UploadStatus.DRAFT,
        )
        every { videoRepository.claimForPublish(userId, videoId) } returns false
        every { channelRepository.findByUserIdAndPlatform(userId, Platform.YOUTUBE) } returns Channel(
            id = 100L,
            userId = userId,
            platform = Platform.YOUTUBE,
            platformChannelId = "channel-youtube",
            channelName = "테스트 채널",
            accessToken = EncryptedToken("encrypted-token"),
        )

        assertFailsWith<IllegalStateException> {
            useCase().publishVideo(
                userId = userId,
                videoId = videoId,
                configs = listOf(
                    PlatformUploadConfig(
                        platform = Platform.YOUTUBE,
                        videoUploadId = 0L,
                        title = "게시 시도",
                        description = null,
                        tags = emptyList(),
                        visibility = Visibility.PUBLIC,
                        thumbnailUrl = null,
                        scheduledAt = null,
                    ),
                ),
            )
        }

        verify(exactly = 0) { videoUploadRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `retry claim is rejected atomically before creating a duplicate event`() {
        val userId = 42L
        val videoId = 901L
        val upload = VideoUpload(
            id = 1L,
            videoId = videoId,
            platform = Platform.YOUTUBE,
            status = UploadStatus.FAILED,
        )
        every { videoRepository.findById(videoId) } returns Video(
            id = videoId,
            userId = userId,
            title = "실패 영상",
            fileUrl = "https://storage.test/retry.mp4",
            status = UploadStatus.PARTIALLY_PUBLISHED,
        )
        every { videoUploadRepository.findByVideoIdAndPlatform(videoId, Platform.YOUTUBE) } returns upload
        every { videoUploadRepository.claimForRetry(upload.id!!) } returns false

        assertFailsWith<IllegalStateException> {
            useCase().retryUpload(userId, videoId, Platform.YOUTUBE.name)
        }

        verify(exactly = 0) { videoUploadRepository.update(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    private fun useCase() = PublishVideoUseCase(
        videoRepository = videoRepository,
        videoUploadRepository = videoUploadRepository,
        videoPlatformMetaRepository = videoPlatformMetaRepository,
        eventPublisher = eventPublisher,
        channelRepository = channelRepository,
        videoUploadPoller = videoUploadPoller,
        userWriteGuard = userWriteGuard,
        scheduleRepository = scheduleRepository,
        platformConfigurationPort = allPlatformsConfigured,
    )

    /**
     * 이 배포에 모든 플랫폼 자격증명이 있는 상태. 대부분의 테스트는 게시 로직을 보므로
     * 설정은 갖춰졌다고 두고, 설정 자체를 다루는 테스트만 아래에서 따로 바꾼다.
     */
    private val allPlatformsConfigured = object : PlatformConfigurationPort {
        override fun status(platform: Platform) = PlatformConfigurationStatus(configured = true)
    }

    /* ---- 플랫폼 설정 확인 ---- */

    /**
     * **설정을 확인할 수 없는 것은 설정된 것이 아니다.**
     *
     * 예전에는 조회 통로가 null 이면 `?.` 로 검사 전체를 건너뛰어, 어댑터가 빠진 배포에서
     * 자격증명 없는 플랫폼으로도 게시가 통과했다. 사용자는 "게시 요청됨" 을 본 뒤 외부
     * 호출 단계에서 조용히 실패했다. 잘못된 배포는 게시가 막히는 쪽으로 틀려야 한다.
     */
    private fun configuredVideo() = Video(
        id = 700L,
        userId = 7L,
        title = "게시할 영상",
        fileUrl = "https://storage.test/original.mp4",
        status = UploadStatus.DRAFT,
    )

    private fun youtubeConfig() = PlatformUploadConfig(
        platform = Platform.YOUTUBE,
        videoUploadId = 0L,
        title = "게시 시도",
        description = null,
        tags = emptyList(),
        visibility = Visibility.PUBLIC,
        thumbnailUrl = null,
        scheduledAt = null,
    )

    private fun useCaseWith(port: PlatformConfigurationPort?) = PublishVideoUseCase(
        videoRepository = videoRepository,
        videoUploadRepository = videoUploadRepository,
        videoPlatformMetaRepository = videoPlatformMetaRepository,
        eventPublisher = eventPublisher,
        channelRepository = channelRepository,
        videoUploadPoller = videoUploadPoller,
        userWriteGuard = userWriteGuard,
        scheduleRepository = scheduleRepository,
        platformConfigurationPort = port,
    )

    @Test
    fun `설정 조회 통로가 없으면 게시를 거부한다`() {
        every { videoRepository.findById(700L) } returns configuredVideo()

        // 인프라 어댑터가 없는 배포 — 확인할 방법이 없으면 통과시키지 않는다.
        val error = assertFailsWith<BusinessException> {
            useCaseWith(null).publishVideo(7L, 700L, listOf(youtubeConfig()))
        }

        assertEquals("PLATFORM_NOT_CONFIGURED", error.code)
        // 외부 게시 이벤트도, 업로드 행도 만들지 않는다.
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        verify(exactly = 0) { videoUploadRepository.save(any()) }
    }

    @Test
    fun `플랫폼이 미설정이면 게시를 거부한다`() {
        every { videoRepository.findById(700L) } returns configuredVideo()
        val unconfigured = object : PlatformConfigurationPort {
            override fun status(platform: Platform) = PlatformConfigurationStatus(
                configured = false,
                reason = "YouTube 연동 설정이 없습니다.",
            )
        }

        val error = assertFailsWith<BusinessException> {
            useCaseWith(unconfigured).publishVideo(7L, 700L, listOf(youtubeConfig()))
        }

        assertEquals("PLATFORM_NOT_CONFIGURED", error.code)
        assertEquals("YouTube 연동 설정이 없습니다.", error.message)
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }
}
