package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channel.FeedItemResult
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.PlatformFeedPortResult
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoFeedUseCaseTest {

    private val channelRepository = mockk<ChannelRepository>()
    private val platformClientPort = mockk<PlatformClientPort>()
    private val tokenEncryptionPort = mockk<TokenEncryptionPort>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val useCase = VideoFeedUseCase(
        channelRepository,
        platformClientPort,
        tokenEncryptionPort,
        videoUploadRepository,
    )

    @Test
    fun `provider feed item includes internal video id only when it matches a local upload`() {
        val channel = Channel(
            id = 101L,
            userId = 7L,
            platform = Platform.YOUTUBE,
            platformChannelId = "channel-1",
            channelName = "내 채널",
            accessToken = EncryptedToken("encrypted"),
        )
        every { channelRepository.findByUserId(7L) } returns listOf(channel)
        every { videoUploadRepository.findByUserId(7L) } returns listOf(
            VideoUpload(
                id = 11L,
                videoId = 99L,
                platform = Platform.YOUTUBE,
                channelId = 101L,
                platformVideoId = "yt-1",
            ),
        )
        every { tokenEncryptionPort.decrypt(channel.accessToken) } returns PlainToken("plain")
        every {
            platformClientPort.listVideos(Platform.YOUTUBE, any(), "channel-1", 20, null)
        } returns PlatformFeedPortResult(
            items = listOf(FeedItemResult(platformVideoId = "yt-1", title = "영상", publishedAt = "2026-08-27T00:00:00Z")),
        )

        val result = useCase.getFeed(7L, null, 0, 20, "recent")

        assertEquals(99L, result.items.single().videoId)
    }

    @Test
    fun `provider-only feed item does not expose a guessed internal id`() {
        val channel = Channel(
            id = 101L,
            userId = 7L,
            platform = Platform.YOUTUBE,
            platformChannelId = "channel-1",
            channelName = "내 채널",
            accessToken = EncryptedToken("encrypted"),
        )
        every { channelRepository.findByUserId(7L) } returns listOf(channel)
        every { videoUploadRepository.findByUserId(7L) } returns emptyList()
        every { tokenEncryptionPort.decrypt(channel.accessToken) } returns PlainToken("plain")
        every {
            platformClientPort.listVideos(Platform.YOUTUBE, any(), "channel-1", 20, null)
        } returns PlatformFeedPortResult(
            items = listOf(FeedItemResult(platformVideoId = "123456", title = "외부 영상", publishedAt = "2026-08-27T00:00:00Z")),
        )

        val result = useCase.getFeed(7L, null, 0, 20, "recent")

        assertNull(result.items.single().videoId)
    }

    @Test
    fun `provider feed error is exposed instead of looking like a successful empty feed`() {
        val channel = Channel(
            id = 101L,
            userId = 7L,
            platform = Platform.YOUTUBE,
            platformChannelId = "channel-1",
            channelName = "내 채널",
            accessToken = EncryptedToken("encrypted"),
        )
        every { channelRepository.findByUserId(7L) } returns listOf(channel)
        every { videoUploadRepository.findByUserId(7L) } returns emptyList()
        every { tokenEncryptionPort.decrypt(channel.accessToken) } returns PlainToken("plain")
        every {
            platformClientPort.listVideos(Platform.YOUTUBE, any(), "channel-1", 20, null)
        } returns PlatformFeedPortResult(
            items = emptyList(),
            errorMessage = "YouTube 영상 목록을 불러오지 못했습니다.",
        )

        val result = useCase.getFeed(7L, null, 0, 20, "recent")

        assertTrue(result.items.isEmpty())
        assertEquals(listOf("YOUTUBE"), result.errors)
    }
}
