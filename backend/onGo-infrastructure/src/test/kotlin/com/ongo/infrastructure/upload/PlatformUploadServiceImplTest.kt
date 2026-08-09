package com.ongo.infrastructure.upload

import com.ongo.application.video.PlatformUploadConfig
import com.ongo.application.video.toPublishOutcome
import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.ChannelStatus
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channel.PlainToken
import com.ongo.infrastructure.external.platform.PlatformClient
import com.ongo.infrastructure.external.platform.PlatformClientFactory
import com.ongo.infrastructure.external.platform.PlatformTokenResult
import com.ongo.infrastructure.external.platform.PlatformUploadResult as ClientUploadResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.net.SocketTimeoutException

class PlatformUploadServiceImplTest {

    private fun channel(
        refreshToken: EncryptedToken? = null,
    ) = Channel(
        id = 1L,
        userId = 7L,
        platform = Platform.YOUTUBE,
        platformChannelId = "yt-channel",
        channelName = "creator",
        accessToken = EncryptedToken("encrypted-token"),
        refreshToken = refreshToken,
        status = ChannelStatus.ACTIVE,
    )

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
            accessToken = EncryptedToken("encrypted-token"),
            status = ChannelStatus.ACTIVE,
        )
        every { tokenEncryptionPort.decrypt(EncryptedToken("encrypted-token")) } returns PlainToken("plain-token")
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

    @Test
    fun `게시 요청의 channelId가 있으면 플랫폼 기본 채널 대신 지정 계정을 사용한다`() {
        val factory = mockk<PlatformClientFactory>()
        val channels = mockk<ChannelRepository>()
        val encryption = mockk<TokenEncryptionPort>()
        val client = mockk<PlatformClient>()
        val requestSlot = slot<com.ongo.infrastructure.external.platform.PlatformUploadRequest>()
        val selected = Channel(
            id = 77L,
            userId = 7L,
            platform = Platform.INSTAGRAM,
            platformChannelId = "second-instagram-account",
            channelName = "second",
            accessToken = EncryptedToken("selected-token"),
            status = ChannelStatus.ACTIVE,
        )

        every { channels.findById(77L) } returns selected
        every { factory.getClient(Platform.INSTAGRAM) } returns client
        every { encryption.decrypt(EncryptedToken("selected-token")) } returns PlainToken("selected-plain-token")
        every { client.uploadVideo(capture(requestSlot)) } returns ClientUploadResult(
            platformVideoId = "media-77",
            platformUrl = "https://instagram.com/reel/media-77",
            status = "PUBLISHED",
        )

        val result = PlatformUploadServiceImpl(factory, channels, encryption, emptyList()).upload(
            config = PlatformUploadConfig(
                platform = Platform.INSTAGRAM,
                videoUploadId = 10L,
                channelId = 77L,
                title = "제목",
                description = "설명",
                tags = emptyList(),
                visibility = Visibility.PUBLIC,
                thumbnailUrl = null,
                scheduledAt = null,
            ),
            fileUrl = "https://storage.example/video.mp4",
            userId = 7L,
        )

        assertThat(requestSlot.captured.platformChannelId).isEqualTo("second-instagram-account")
        assertThat(requestSlot.captured.accessToken).isEqualTo("selected-plain-token")
        assertThat(result.published).isTrue()
    }

    @Test
    fun `TikTok 비동기 완료 상태는 공개 영상 ID와 사용 가능한 URL로 정규화한다`() {
        val factory = mockk<PlatformClientFactory>()
        val channelRepository = mockk<ChannelRepository>()
        val tokenEncryptionPort = mockk<TokenEncryptionPort>()
        val client = mockk<PlatformClient>()
        every { factory.getClient(Platform.TIKTOK) } returns client
        every { channelRepository.findByUserIdAndPlatform(7L, Platform.TIKTOK) } returns Channel(
            id = 2L,
            userId = 7L,
            platform = Platform.TIKTOK,
            platformChannelId = "creator",
            channelName = "creator",
            accessToken = EncryptedToken("encrypted-token"),
            status = ChannelStatus.ACTIVE,
        )
        every { tokenEncryptionPort.decrypt(EncryptedToken("encrypted-token")) } returns PlainToken("plain-token")
        every { client.getVideoStatus("publish-1", "plain-token") } returns com.ongo.infrastructure.external.platform.PlatformVideoStatus(
            platformVideoId = "video-1",
            status = "PUBLISH_COMPLETE",
        )

        val service = PlatformUploadServiceImpl(factory, channelRepository, tokenEncryptionPort, emptyList())
        val result = service.poll(Platform.TIKTOK, "publish-1", 7L)

        assertThat(result.success).isTrue()
        assertThat(result.published).isTrue()
        assertThat(result.platformVideoId).isEqualTo("video-1")
        assertThat(result.platformUrl).isEqualTo("https://www.tiktok.com/video/video-1")
    }

    @Test
    fun `상태 조회 응답에 URL이 없어도 최초 응답의 유효한 URL을 보존한다`() {
        val factory = mockk<PlatformClientFactory>()
        val channels = mockk<ChannelRepository>()
        val encryption = mockk<TokenEncryptionPort>()
        val client = mockk<PlatformClient>()
        every { factory.getClient(Platform.WORDPRESS) } returns client
        every { channels.findByUserIdAndPlatform(7L, Platform.WORDPRESS) } returns Channel(
            id = 3L,
            userId = 7L,
            platform = Platform.WORDPRESS,
            platformChannelId = "site-1",
            channelName = "site",
            accessToken = EncryptedToken("encrypted-token"),
            status = ChannelStatus.ACTIVE,
        )
        every { encryption.decrypt(EncryptedToken("encrypted-token")) } returns PlainToken("plain-token")
        every { client.getVideoStatus("post-1", "plain-token") } returns com.ongo.infrastructure.external.platform.PlatformVideoStatus(
            platformVideoId = "post-1",
            status = "publish",
        )

        val result = PlatformUploadServiceImpl(factory, channels, encryption, emptyList()).poll(
            Platform.WORDPRESS,
            "post-1",
            7L,
            "https://example.wordpress.com/?p=post-1",
        )

        assertThat(result.published).isTrue()
        assertThat(result.platformUrl).isEqualTo("https://example.wordpress.com/?p=post-1")
    }

    @Test
    fun `완료 상태라도 URL이 없으면 확인 불가로 남긴다`() {
        val factory = mockk<PlatformClientFactory>()
        val channels = mockk<ChannelRepository>()
        val encryption = mockk<TokenEncryptionPort>()
        val client = mockk<PlatformClient>()
        every { factory.getClient(Platform.TUMBLR) } returns client
        every { channels.findByUserIdAndPlatform(7L, Platform.TUMBLR) } returns Channel(
            id = 4L,
            userId = 7L,
            platform = Platform.TUMBLR,
            platformChannelId = "blog",
            channelName = "blog",
            accessToken = EncryptedToken("encrypted-token"),
            status = ChannelStatus.ACTIVE,
        )
        every { encryption.decrypt(EncryptedToken("encrypted-token")) } returns PlainToken("plain-token")
        every { client.getVideoStatus("post-2", "plain-token") } returns com.ongo.infrastructure.external.platform.PlatformVideoStatus(
            platformVideoId = "post-2",
            status = "published",
        )

        val result = PlatformUploadServiceImpl(factory, channels, encryption, emptyList()).poll(
            Platform.TUMBLR,
            "post-2",
            7L,
        )

        assertThat(result.success).isFalse()
        assertThat(result.confirmation).isEqualTo(com.ongo.application.video.PublishConfirmation.UNKNOWN)
    }

    @Test
    fun `4xx 게시 오류는 예외를 밖으로 재전파하지 않고 실패 결과로 남긴다`() {
        val factory = mockk<PlatformClientFactory>()
        val channels = mockk<ChannelRepository>()
        val encryption = mockk<TokenEncryptionPort>()
        val client = mockk<PlatformClient>()
        val badRequest = HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST,
            "Bad Request",
            HttpHeaders.EMPTY,
            ByteArray(0),
            Charsets.UTF_8,
        )

        every { factory.getClient(Platform.YOUTUBE) } returns client
        every { channels.findByUserIdAndPlatform(7L, Platform.YOUTUBE) } returns channel()
        every { encryption.decrypt(EncryptedToken("encrypted-token")) } returns PlainToken("plain-token")
        every { client.uploadVideo(any()) } throws badRequest

        val result = PlatformUploadServiceImpl(factory, channels, encryption, emptyList()).upload(
            config = PlatformUploadConfig(
                platform = Platform.YOUTUBE,
                videoUploadId = 10L,
                title = "제목",
                description = null,
                tags = emptyList(),
                visibility = Visibility.PUBLIC,
                thumbnailUrl = null,
                fileSize = 100,
                scheduledAt = null,
            ),
            fileUrl = "https://storage.example/video.mp4",
            userId = 7L,
        )

        assertThat(result.success).isFalse()
        assertThat(result.published).isFalse()
        assertThat(result.errorMessage).isNotBlank()
    }

    @Test
    fun `타임아웃은 중복 게시 방지를 위해 확인 불가 결과로 남긴다`() {
        val factory = mockk<PlatformClientFactory>()
        val channels = mockk<ChannelRepository>()
        val encryption = mockk<TokenEncryptionPort>()
        val client = mockk<PlatformClient>()

        every { factory.getClient(Platform.YOUTUBE) } returns client
        every { channels.findByUserIdAndPlatform(7L, Platform.YOUTUBE) } returns channel()
        every { encryption.decrypt(EncryptedToken("encrypted-token")) } returns PlainToken("plain-token")
        every { client.uploadVideo(any()) } throws SocketTimeoutException("read timed out")

        val result = PlatformUploadServiceImpl(factory, channels, encryption, emptyList()).upload(
            config = PlatformUploadConfig(
                platform = Platform.YOUTUBE,
                videoUploadId = 10L,
                title = "제목",
                description = null,
                tags = emptyList(),
                visibility = Visibility.PUBLIC,
                thumbnailUrl = null,
                fileSize = 100,
                scheduledAt = null,
            ),
            fileUrl = "https://storage.example/video.mp4",
            userId = 7L,
        )

        assertThat(result.success).isFalse()
        assertThat(result.confirmation).isEqualTo(com.ongo.application.video.PublishConfirmation.UNKNOWN)
        assertThat(result.toPublishOutcome()).isInstanceOf(com.ongo.application.video.PublishOutcome.Unconfirmed::class.java)
    }

    @Test
    fun `401이면 저장된 refresh token으로 한 번 갱신한 뒤 새 토큰으로 재시도한다`() {
        val factory = mockk<PlatformClientFactory>()
        val channels = mockk<ChannelRepository>()
        val encryption = mockk<TokenEncryptionPort>()
        val client = mockk<PlatformClient>()
        val requests = mutableListOf<String>()
        val unauthorized = HttpClientErrorException.create(
            HttpStatus.UNAUTHORIZED,
            "Unauthorized",
            HttpHeaders.EMPTY,
            ByteArray(0),
            Charsets.UTF_8,
        )

        every { factory.getClient(Platform.YOUTUBE) } returns client
        every { channels.findByUserIdAndPlatform(7L, Platform.YOUTUBE) } returns channel(EncryptedToken("encrypted-refresh"))
        every { encryption.decrypt(EncryptedToken("encrypted-token")) } returns PlainToken("old-token")
        every { encryption.decrypt(EncryptedToken("encrypted-refresh")) } returns PlainToken("refresh-token")
        every { encryption.encrypt(any()) } returns EncryptedToken("encrypted-new")
        every { channels.update(any()) } answers { firstArg() }
        every { client.refreshToken("refresh-token") } returns PlatformTokenResult(
            accessToken = "new-token",
            refreshToken = null,
            expiresIn = 3600,
        )
        every { client.uploadVideo(any()) } answers {
            val request = firstArg<com.ongo.infrastructure.external.platform.PlatformUploadRequest>()
            requests += request.accessToken
            if (requests.size == 1) throw unauthorized
            ClientUploadResult("video-1", "https://youtube.com/watch?v=video-1", "PUBLISHED")
        }

        val result = PlatformUploadServiceImpl(factory, channels, encryption, emptyList()).upload(
            config = PlatformUploadConfig(
                platform = Platform.YOUTUBE,
                videoUploadId = 10L,
                title = "제목",
                description = null,
                tags = emptyList(),
                visibility = Visibility.PUBLIC,
                thumbnailUrl = null,
                fileSize = 100,
                scheduledAt = null,
            ),
            fileUrl = "https://storage.example/video.mp4",
            userId = 7L,
        )

        assertThat(requests).containsExactly("old-token", "new-token")
        assertThat(result.success).isTrue()
        assertThat(result.published).isTrue()
    }
}
