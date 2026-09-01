package com.ongo.infrastructure.upload

import com.ongo.application.video.PlatformUploadConfig
import com.ongo.application.video.toPublishOutcome
import com.ongo.common.enums.Platform
import com.ongo.common.enums.MediaType
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
import io.mockk.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
                customSettingsJson = "{\"privacy_level\":\"PUBLIC\"}",
                fileSize = 100,
                scheduledAt = null,
            ),
            fileUrl = "https://storage.example/video.mp4",
            userId = 7L,
        )

        assertThat(requestSlot.captured.accessToken.value).isEqualTo("plain-token")
        assertThat(requestSlot.captured.customSettingsJson).isEqualTo("{\"privacy_level\":\"PUBLIC\"}")
        assertThat(result.success).isTrue()
        assertThat(result.published).isTrue()
        assertThat(result.platformUrl).isEqualTo("https://instagram.com/reel/media-1")
    }

    @Test
    fun `이미지 게시 요청은 이미지 전용 클라이언트 메서드를 사용한다`() {
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
        every { client.uploadImage(capture(requestSlot)) } returns ClientUploadResult(
            platformVideoId = "image-1",
            platformUrl = "https://instagram.com/p/image-1",
            status = "PUBLISHED",
        )

        val result = PlatformUploadServiceImpl(factory, channelRepository, tokenEncryptionPort, emptyList()).upload(
            config = PlatformUploadConfig(
                platform = Platform.INSTAGRAM,
                videoUploadId = 10L,
                title = "이미지",
                description = "설명",
                tags = emptyList(),
                visibility = Visibility.PUBLIC,
                thumbnailUrl = null,
                scheduledAt = null,
                mediaType = MediaType.IMAGE,
            ),
            fileUrl = "https://storage.example/image.jpg",
            userId = 7L,
        )

        assertThat(requestSlot.captured.mediaType).isEqualTo(MediaType.IMAGE)
        assertThat(requestSlot.captured.fileUrl).isEqualTo("https://storage.example/image.jpg")
        assertThat(result.published).isTrue()
        verify(exactly = 0) { client.uploadVideo(any()) }
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
        assertThat(requestSlot.captured.accessToken.value).isEqualTo("selected-plain-token")
        assertThat(result.published).isTrue()
    }

    @Test
    fun `외부 클라이언트의 실패 상태는 처리중이 아니라 실패 결과로 보존한다`() {
        val factory = mockk<PlatformClientFactory>()
        val channels = mockk<ChannelRepository>()
        val encryption = mockk<TokenEncryptionPort>()
        val client = mockk<PlatformClient>()

        every { factory.getClient(Platform.INSTAGRAM) } returns client
        every { channels.findByUserIdAndPlatform(7L, Platform.INSTAGRAM) } returns Channel(
            id = 1L,
            userId = 7L,
            platform = Platform.INSTAGRAM,
            platformChannelId = "ig-user",
            channelName = "creator",
            accessToken = EncryptedToken("encrypted-token"),
            status = ChannelStatus.ACTIVE,
        )
        every { encryption.decrypt(EncryptedToken("encrypted-token")) } returns PlainToken("plain-token")
        every { client.uploadVideo(any()) } returns ClientUploadResult(
            platformVideoId = "media-rejected",
            platformUrl = "",
            status = "REJECTED",
        )

        val result = PlatformUploadServiceImpl(factory, channels, encryption, emptyList()).upload(
            config = PlatformUploadConfig(
                platform = Platform.INSTAGRAM,
                videoUploadId = 10L,
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

        assertThat(result.success).isFalse()
        assertThat(result.published).isFalse()
        assertThat(result.errorMessage).contains("거부")
        assertThat(result.toPublishOutcome()).isInstanceOf(com.ongo.application.video.PublishOutcome.Failed::class.java)
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
        assertThat(result.platformUrl).isEqualTo("https://www.tiktok.com/@creator/video/video-1")
    }

    @Test
    fun `TikTok publish_id만 있고 공개 영상 ID가 없으면 완료 URL을 추정하지 않는다`() {
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
            platformVideoId = "publish-1",
            status = "PUBLISH_COMPLETE",
        )

        val service = PlatformUploadServiceImpl(factory, channelRepository, tokenEncryptionPort, emptyList())
        val result = service.poll(Platform.TIKTOK, "publish-1", 7L)

        assertThat(result.success).isFalse()
        assertThat(result.published).isFalse()
        assertThat(result.platformUrl).isNull()
        assertThat(result.confirmation).isEqualTo(com.ongo.application.video.PublishConfirmation.UNKNOWN)
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
    fun `429는 Retry-After를 존중해 재시도하고 성공하면 게시 결과를 반환한다`() {
        val factory = mockk<PlatformClientFactory>()
        val channels = mockk<ChannelRepository>()
        val encryption = mockk<TokenEncryptionPort>()
        val client = mockk<PlatformClient>()
        val retryHeaders = HttpHeaders().apply { set("Retry-After", "1") }
        val rateLimited = HttpClientErrorException.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "Too Many Requests",
            retryHeaders,
            ByteArray(0),
            Charsets.UTF_8,
        )
        var attempts = 0

        every { factory.getClient(Platform.YOUTUBE) } returns client
        every { channels.findByUserIdAndPlatform(7L, Platform.YOUTUBE) } returns channel()
        every { encryption.decrypt(EncryptedToken("encrypted-token")) } returns PlainToken("plain-token")
        every { client.uploadVideo(any()) } answers {
            attempts++
            if (attempts == 1) throw rateLimited
            ClientUploadResult(
                platformVideoId = "video-429-recovered",
                platformUrl = "https://youtube.com/watch?v=video-429-recovered",
                status = "PUBLISHED",
            )
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

        assertThat(attempts).isEqualTo(2)
        assertThat(result.success).isTrue()
        assertThat(result.published).isTrue()
        assertThat(result.platformVideoId).isEqualTo("video-429-recovered")
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
        io.mockk.verify(exactly = 1) { client.uploadVideo(any()) }
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
            requests += request.accessToken.value
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

    @Test
    fun `직접 스트리밍 writer가 반환한 401도 토큰 갱신 후 세션을 재시도한다`() {
        val factory = mockk<PlatformClientFactory>()
        val channels = mockk<ChannelRepository>()
        val encryption = mockk<TokenEncryptionPort>()
        val client = mockk<PlatformClient>()
        val writer = mockk<com.ongo.application.video.PlatformStreamWriter>()
        val tokens = mutableListOf<String>()
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("test video"))
        server.enqueue(MockResponse().setBody("test video"))
        server.start()

        try {
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
            every { writer.initSession(any(), any(), any(), any(), any()) } answers {
                tokens += secondArg<PlainToken>().value
                "session"
            }
            every { writer.writeChunk(any(), any(), any()) } just Runs
            every { writer.abort() } just Runs
            every { writer.complete() } returnsMany listOf(
                com.ongo.application.video.PlatformUploadResult(
                    success = false,
                    published = false,
                    errorMessage = "Unauthorized",
                    httpStatus = 401,
                ),
                com.ongo.application.video.PlatformUploadResult(
                    success = true,
                    platformVideoId = "video-1",
                    platformUrl = "https://youtube.com/watch?v=video-1",
                    published = true,
                ),
            )
            val streamFactory = object : com.ongo.application.video.PlatformStreamWriterFactory {
                override val platform = Platform.YOUTUBE
                override fun createWriter() = writer
            }

            val result = PlatformUploadServiceImpl(factory, channels, encryption, listOf(streamFactory)).upload(
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
                fileUrl = server.url("/video.mp4").toString(),
                userId = 7L,
            )

            assertThat(tokens).containsExactly("old-token", "new-token")
            assertThat(result.published).isTrue()
        } finally {
            server.shutdown()
        }
    }

    /**
     * Naver Clip 은 연동 조회용 클라이언트 빈이 **등록돼 있다.** 예전 `supports` 는 그
     * 사실만 보고 true 를 돌려줬고, 그래서 호출자(리스너·폴러)의 "지원되지 않는 플랫폼"
     * 안전망이 비껴갔다. 그 뒤 upload 가 NaverClipClient.uploadVideo() 까지 내려가
     * "StreamPublishUseCase를 사용하세요" 라는 개발자용 문구를 고객 실패 사유로 남겼다.
     *
     * 빈이 있어도 게시 경로가 없으면 false 여야 한다.
     */
    @Test
    fun `업로드 API가 없는 플랫폼은 클라이언트 빈이 있어도 supports가 false다`() {
        val factory = mockk<PlatformClientFactory>()
        val client = mockk<PlatformClient>()
        every { factory.getClient(Platform.NAVER_CLIP) } returns client

        val service = PlatformUploadServiceImpl(
            platformClientFactory = factory,
            channelRepository = mockk(),
            tokenEncryptionPort = mockk(),
            streamWriterFactories = emptyList(),
        )

        assertThat(service.supports(Platform.NAVER_CLIP)).isFalse()
        // 게시 경로가 있는 플랫폼은 그대로 지원해야 한다 — 가드가 과하게 잡으면 매출이 멈춘다.
        every { factory.getClient(Platform.YOUTUBE) } returns client
        assertThat(service.supports(Platform.YOUTUBE)).isTrue()
    }

    /**
     * `supports` 를 거치지 않은 직접 호출도 외부 API·미구현 분기로 내려가면 안 된다.
     * 채널 조회조차 하지 않고 사용자용 문장으로 끝난다.
     */
    @Test
    fun `업로드 API가 없는 플랫폼은 클라이언트를 부르지 않고 정직한 사유로 실패한다`() {
        val factory = mockk<PlatformClientFactory>(relaxed = true)
        val channelRepository = mockk<ChannelRepository>()
        val tokenEncryptionPort = mockk<TokenEncryptionPort>()

        val service = PlatformUploadServiceImpl(
            platformClientFactory = factory,
            channelRepository = channelRepository,
            tokenEncryptionPort = tokenEncryptionPort,
            streamWriterFactories = emptyList(),
        )

        val result = service.upload(
            config = PlatformUploadConfig(
                platform = Platform.NAVER_CLIP,
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
        assertThat(result.retryable).isFalse()
        // 내부 마이그레이션 안내가 사용자에게 새지 않는다.
        assertThat(result.errorMessage).doesNotContain("StreamPublishUseCase")
        assertThat(result.errorMessage).doesNotContain("uploadVideo")
        assertThat(result.errorMessage).contains("Naver Clip")

        // 외부 경계도, 채널 조회도 건드리지 않는다.
        verify(exactly = 0) { factory.getClient(any()) }
        verify { channelRepository wasNot Called }
        verify { tokenEncryptionPort wasNot Called }
    }
}
