package com.ongo.application.channel

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.AuthProvider
import com.ongo.common.exception.BusinessException
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.ChannelStatus
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.PlatformChannelInfoResult
import com.ongo.domain.channel.PlatformOAuth2Port
import com.ongo.domain.channel.PlatformOAuth2TokenResult
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.domain.workspace.WorkspaceRepository
import com.ongo.application.platform.PlatformConfigurationPort
import com.ongo.application.platform.PlatformConfigurationStatus
import com.ongo.application.channel.dto.ConnectChannelRequest
import com.ongo.domain.user.User
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.LocalDateTime
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ChannelUseCaseTest {

    private val channels = mockk<ChannelRepository>()
    private val users = mockk<UserRepository>()
    private val oauth = mockk<PlatformOAuth2Port>()
    private val clients = mockk<PlatformClientPort>()
    private val encryption = mockk<TokenEncryptionPort>()
    private val videoUploads = mockk<VideoUploadRepository>()
    private val workspaces = mockk<WorkspaceRepository>()
    private val stateManager = ChannelOAuthStateManager("test-oauth-state-secret-that-is-at-least-32-chars")
    private val useCase = ChannelUseCase(channels, users, oauth, clients, encryption, videoUploads, workspaces, stateManager)

    @Test
    fun `활성 lease가 있는 예약 게시가 남아 있으면 채널 해제를 거부한다`() {
        val channel = Channel(
            id = 9L,
            userId = 7L,
            platform = Platform.YOUTUBE,
            platformChannelId = "channel-9",
            channelName = "channel",
            accessToken = mockk(),
        )
        every { channels.findById(9L) } returns channel
        every { videoUploads.findByUserId(7L) } returns listOf(
            VideoUpload(
                id = 11L,
                videoId = 22L,
                channelId = 9L,
                platform = Platform.YOUTUBE,
                status = UploadStatus.UPLOADING,
                scheduledAt = LocalDateTime.now().plusMinutes(5),
                leaseUntil = LocalDateTime.now().plusMinutes(1),
            ),
        )
        every { videoUploads.cancelScheduledUploadsByChannelId(9L, any()) } returns 0

        assertFailsWith<BusinessException> {
            useCase.disconnectChannel(7L, 9L)
        }

        verify(exactly = 0) { encryption.decrypt(any()) }
        verify(exactly = 0) { clients.revokeToken(any(), any()) }
        verify(exactly = 0) { channels.delete(any()) }
    }

    @Test
    fun `운영 설정이 없는 플랫폼은 OAuth 교환 전에 명시적으로 거부한다`() {
        val configuration = mockk<PlatformConfigurationPort>()
        val guardedUseCase = ChannelUseCase(
            channels,
            users,
            oauth,
            clients,
            encryption,
            videoUploads,
            workspaces,
            stateManager,
            configuration,
        )
        every { users.findById(7L) } returns User(
            id = 7L,
            email = "creator@example.com",
            name = "creator",
            provider = AuthProvider.GOOGLE,
            providerId = "google-7",
        )
        every { configuration.status(Platform.TIKTOK) } returns PlatformConfigurationStatus(
            configured = false,
            reason = "TikTok 플랫폼 연동 설정이 운영 서버에 구성되지 않았습니다.",
        )

        val error = assertFailsWith<BusinessException> {
            guardedUseCase.connectChannel(
                7L,
                Platform.TIKTOK.name,
                ConnectChannelRequest(
                    "oauth-code",
                    "https://ongo.test/auth/channel-callback",
                    stateManager.issue(7L, Platform.TIKTOK, "https://ongo.test/auth/channel-callback", "state"),
                ),
            )
        }

        kotlin.test.assertEquals("PLATFORM_NOT_CONFIGURED", error.code)
        verify(exactly = 0) { oauth.exchangeCodeForTokens(any(), any(), any(), any()) }
    }

    /**
     * **설정을 확인할 수 없는 것은 설정된 것이 아니다.**
     *
     * 예전에는 조회 통로가 null 이면 `?.` 로 검사를 건너뛰어, 어댑터가 빠진 배포에서
     * 자격증명 없는 플랫폼에도 채널을 연결할 수 있었다. 그 채널은 연결 목록에 정상으로
     * 보이다가 게시 시점에야 실패한다. 잘못된 배포는 연결이 막히는 쪽으로 틀려야 한다.
     */
    @Test
    fun `설정 조회 통로가 없으면 채널 연결을 거부한다`() {
        every { users.findById(7L) } returns User(
            id = 7L,
            email = "creator@example.com",
            name = "creator",
            provider = AuthProvider.GOOGLE,
            providerId = "google-7",
        )

        // `useCase` 는 조회 통로 없이 만들어진, 어댑터가 빠진 배포다.
        val error = assertFailsWith<BusinessException> {
            useCase.connectChannel(
                7L,
                Platform.TIKTOK.name,
                ConnectChannelRequest(
                    "oauth-code",
                    "https://ongo.test/auth/channel-callback",
                    stateManager.issue(7L, Platform.TIKTOK, "https://ongo.test/auth/channel-callback", "state"),
                ),
            )
        }

        kotlin.test.assertEquals("PLATFORM_NOT_CONFIGURED", error.code)
        // OAuth 코드 교환까지 가지 않는다 — 외부 호출 전에 끊는다.
        verify(exactly = 0) { oauth.exchangeCodeForTokens(any(), any(), any(), any()) }
        verify(exactly = 0) { channels.save(any()) }
    }

    @Test
    fun `OAuth state가 없으면 외부 토큰 교환을 실행하지 않는다`() {
        val error = assertFailsWith<BusinessException> {
            useCase.connectChannel(
                7L,
                Platform.YOUTUBE.name,
                ConnectChannelRequest(
                    "oauth-code",
                    "https://ongo.test/auth/channel-callback",
                    state = "forged-state",
                ),
            )
        }

        kotlin.test.assertEquals("OAUTH_STATE_INVALID", error.code)
        verify(exactly = 0) { oauth.exchangeCodeForTokens(any(), any(), any(), any()) }
    }

    @Test
    fun `재연결 응답에 refresh token이 없으면 기존 토큰을 보존한다`() {
        val configuration = mockk<PlatformConfigurationPort>()
        val oldRefresh = EncryptedToken("encrypted-old-refresh")
        val existing = Channel(
            id = 9L,
            userId = 7L,
            platform = Platform.YOUTUBE,
            platformChannelId = "channel-9",
            channelName = "old-channel",
            accessToken = EncryptedToken("encrypted-old-access"),
            refreshToken = oldRefresh,
            status = ChannelStatus.EXPIRED,
        )
        val updated = slot<Channel>()
        val reconnectingUseCase = ChannelUseCase(
            channels,
            users,
            oauth,
            clients,
            encryption,
            videoUploads,
            workspaces,
            stateManager,
            configuration,
        )

        every { users.findById(7L) } returns User(
            id = 7L,
            email = "creator@example.com",
            name = "creator",
            provider = AuthProvider.GOOGLE,
            providerId = "google-7",
        )
        every { configuration.status(Platform.YOUTUBE) } returns PlatformConfigurationStatus(configured = true)
        every { channels.findByUserIdAndPlatform(7L, Platform.YOUTUBE) } returns existing
        every {
            oauth.exchangeCodeForTokens(
                Platform.YOUTUBE,
                "oauth-code",
                "https://ongo.test/auth/channel-callback",
                null,
            )
        } returns PlatformOAuth2TokenResult(
            accessToken = "new-access",
            refreshToken = null,
            expiresIn = 3600L,
        )
        every { clients.getChannelInfo(Platform.YOUTUBE, PlainToken("new-access")) } returns PlatformChannelInfoResult(
            channelId = "channel-9",
            channelName = "new-channel",
            channelUrl = "https://youtube.com/channel/channel-9",
            subscriberCount = 12L,
            profileImageUrl = null,
        )
        every { encryption.encrypt(PlainToken("new-access")) } returns EncryptedToken("encrypted-new-access")
        every { channels.findByUserIdAndPlatformChannelId(7L, Platform.YOUTUBE, "channel-9") } returns existing
        every { channels.update(capture(updated)) } answers { firstArg() }

        reconnectingUseCase.connectChannel(
            7L,
            Platform.YOUTUBE.name,
            ConnectChannelRequest(
                "oauth-code",
                "https://ongo.test/auth/channel-callback",
                stateManager.issue(7L, Platform.YOUTUBE, "https://ongo.test/auth/channel-callback", "state"),
            ),
        )

        assertEquals(oldRefresh, updated.captured.refreshToken)
        assertEquals(EncryptedToken("encrypted-new-access"), updated.captured.accessToken)
        assertEquals(ChannelStatus.ACTIVE, updated.captured.status)
    }
}
