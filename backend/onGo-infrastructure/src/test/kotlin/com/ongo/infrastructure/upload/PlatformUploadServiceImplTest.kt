package com.ongo.infrastructure.upload

import com.ongo.application.video.PlatformUploadConfig
import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.ChannelStatus
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.infrastructure.external.platform.PlatformClient
import com.ongo.infrastructure.external.platform.PlatformClientFactory
import com.ongo.infrastructure.external.platform.PlatformUploadResult as ClientUploadResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformUploadServiceImplTest {

    @Test
    fun `Instagram 클라이언트의 PUBLISHED 결과를 게시 완료로 보존하고 저장 토큰은 복호화한다`() {
        val factory = mockk<PlatformClientFactory>()
        val channelRepository = mockk<ChannelRepository>()
        val tokenEncryptionPort = mockk<TokenEncryptionPort>()
        val client = mockk<PlatformClient>()
        val requestSlot = slot<com.ongo.infrastructure.external.platform.PlatformUploadRequest>()

        every { factory.getClient(Platform.INSTAGRAM) } returns client
        every { channelRepository.findByUserIdAndPlatform(7L, Platform.INSTAGRAM) } returns Channel(
            id = 1L,
            userId = 7L,
            platform = Platform.INSTAGRAM,
            platformChannelId = "ig-user",
            channelName = "creator",
            accessToken = "encrypted-token",
            status = ChannelStatus.ACTIVE,
        )
        every { tokenEncryptionPort.decrypt("encrypted-token") } returns "plain-token"
        every { client.uploadVideo(capture(requestSlot)) } returns ClientUploadResult(
            platformVideoId = "media-1",
            platformUrl = "https://instagram.com/reel/media-1",
            status = "PUBLISHED",
        )

        val service = PlatformUploadServiceImpl(
            platformClientFactory = factory,
            channelRepository = channelRepository,
            tokenEncryptionPort = tokenEncryptionPort,
            streamWriterFactories = emptyList(),
        )

        val result = service.upload(
            config = PlatformUploadConfig(
                platform = Platform.INSTAGRAM,
                videoUploadId = 10L,
                title = "제목",
                description = "설명",
                tags = listOf("tag"),
                visibility = Visibility.PUBLIC,
                thumbnailUrl = null,
                fileSize = 100,
                scheduledAt = null,
            ),
            fileUrl = "https://storage.example/video.mp4",
            userId = 7L,
        )

        assertThat(requestSlot.captured.accessToken).isEqualTo("plain-token")
        assertThat(result.success).isTrue()
        assertThat(result.published).isTrue()
        assertThat(result.platformUrl).isEqualTo("https://instagram.com/reel/media-1")
    }
}
