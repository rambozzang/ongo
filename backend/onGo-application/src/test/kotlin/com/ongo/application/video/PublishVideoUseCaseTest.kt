package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.EncryptedToken
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

class PublishVideoUseCaseTest {
    private val videoRepository = mockk<VideoRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val videoPlatformMetaRepository = mockk<VideoPlatformMetaRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>()
    private val channelRepository = mockk<ChannelRepository>()
    private val videoUploadPoller = mockk<VideoUploadPoller>(relaxed = true)

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
        every { videoRepository.update(any()) } answers { firstArg() }
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
}
