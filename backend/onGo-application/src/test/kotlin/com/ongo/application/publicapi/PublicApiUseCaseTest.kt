package com.ongo.application.publicapi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.video.PlatformUploadStatus
import com.ongo.application.video.PublishResult
import com.ongo.application.video.PublishVideoUseCase
import com.ongo.application.video.UploadVideoUseCase
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.ChannelStatus
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.publicapi.PublicApiPost
import com.ongo.domain.publicapi.PublicApiPostRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PublicApiUseCaseTest {

    private val channels = mockk<ChannelRepository>()
    private val posts = mockk<PublicApiPostRepository>()
    private val videos = mockk<VideoRepository>()
    private val uploads = mockk<VideoUploadRepository>()
    private val uploadVideo = mockk<UploadVideoUseCase>()
    private val publishVideo = mockk<PublishVideoUseCase>()
    private val useCase = PublicApiUseCase(
        channels, posts, videos, uploads, uploadVideo, publishVideo, jacksonObjectMapper(),
    )

    private val channel = Channel(
        id = 7,
        userId = 1,
        platform = Platform.YOUTUBE,
        platformChannelId = "yt-7",
        channelName = "내 채널",
        accessToken = EncryptedToken("encrypted"),
        status = ChannelStatus.ACTIVE,
    )

    @Test
    fun `integrations는 소유한 연결 계정만 Postiz 형태로 반환한다`() {
        every { channels.findByUserId(1) } returns listOf(channel)

        val result = useCase.integrations(1)

        assertEquals("7", result.single().id)
        assertEquals("youtube", result.single().provider)
        assertEquals("yt-7", result.single().identifier)
    }

    @Test
    fun `now 게시가 내부 durable publisher를 호출하고 post id를 반환한다`() {
        every { videos.findById(11) } returns Video(id = 11, userId = 1, title = "원본", fileUrl = "https://cdn/video.mp4")
        every { channels.findById(7) } returns channel
        every { posts.save(any()) } answers { firstArg<PublicApiPost>().copy(id = 21) }
        every { posts.update(any()) } answers { firstArg() }
        every { uploads.findByVideoId(11) } returns listOf(
            VideoUpload(id = 91, videoId = 11, platform = Platform.YOUTUBE, channelId = 7, status = UploadStatus.UPLOADING),
        )
        every { publishVideo.publishVideo(any(), any(), any()) } returns
            PublishResult(11, listOf(PlatformUploadStatus(Platform.YOUTUBE, UploadStatus.UPLOADING)))

        val response = useCase.create(
            1,
            CreatePublicPostRequest(
                type = "now",
                videoId = 11,
                posts = listOf(
                    PublicPostItem(
                        integration = PublicIntegrationRef("7"),
                        value = listOf(PublicPostValue(title = "제목", content = "설명", tags = listOf("#one"))),
                    ),
                ),
            ),
        )

        assertEquals("21", response.id)
        assertEquals("processing", response.status)
        assertEquals(null, response.error)
        verify(exactly = 1) { publishVideo.publishVideo(1, 11, any()) }
    }

    @Test
    fun `다른 사용자의 videoId는 공개 API에서도 거부한다`() {
        every { videos.findById(11) } returns Video(id = 11, userId = 999, title = "남의 영상", fileUrl = "https://cdn/video.mp4")

        assertFailsWith<com.ongo.common.exception.ForbiddenException> {
            useCase.create(
                1,
                CreatePublicPostRequest(
                    videoId = 11,
                    posts = listOf(PublicPostItem(PublicIntegrationRef("7"))),
                ),
            )
        }
        verify(exactly = 0) { posts.save(any()) }
    }
}
