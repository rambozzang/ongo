package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.AccountFrozenException
import com.ongo.common.exception.ForbiddenException
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.accountdeletion.UserWriteGuard
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

class PublishVideoUseCaseTest {
    private val videoRepository = mockk<VideoRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val videoPlatformMetaRepository = mockk<VideoPlatformMetaRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>()
    private val channelRepository = mockk<ChannelRepository>()
    private val videoUploadPoller = mockk<VideoUploadPoller>(relaxed = true)
    private val userWriteGuard = mockk<UserWriteGuard>(relaxed = true)

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
    fun `publishes one durable upload row for all seven supported video channels`() {
        val userId = 42L
        val videoId = 900L
        val platforms = listOf(
            Platform.YOUTUBE,
            Platform.TIKTOK,
            Platform.NAVER_CLIP,
            Platform.TWITTER,
            Platform.INSTAGRAM,
            Platform.THREADS,
            Platform.FACEBOOK,
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
                    tags = listOf("#ongo"),
                    visibility = Visibility.PUBLIC,
                    thumbnailUrl = null,
                    scheduledAt = null,
                )
            },
        )

        assertEquals(platforms, result.uploads.map { it.platform })
        assertEquals(platforms, event.captured.platformConfigs.map { it.platform })
        assertEquals(7, event.captured.platformConfigs.map { it.videoUploadId }.distinct().size)
        verify(exactly = 7) { videoUploadRepository.save(any()) }
        verify(exactly = 7) { videoPlatformMetaRepository.save(any()) }
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
    )
}
