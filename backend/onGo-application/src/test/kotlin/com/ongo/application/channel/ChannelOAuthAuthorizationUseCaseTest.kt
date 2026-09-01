package com.ongo.application.channel

import com.ongo.application.platform.PlatformConfigurationPort
import com.ongo.application.platform.PlatformConfigurationStatus
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import com.ongo.domain.channel.PlatformOAuthAuthorizationPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChannelOAuthAuthorizationUseCaseTest {
    private val authorizationPort = mockk<PlatformOAuthAuthorizationPort>()
    private val configurationPort = mockk<PlatformConfigurationPort>()

    private fun useCase() = ChannelOAuthAuthorizationUseCase(
        authorizationPort = authorizationPort,
        platformConfigurationPort = configurationPort,
        stateManager = ChannelOAuthStateManager("test-oauth-state-secret-that-is-at-least-32-chars"),
        allowedOrigins = "https://ongo.example.com,http://localhost:5173",
    )

    @Test
    fun `configured platform returns server-built authorization URL`() {
        every { configurationPort.status(Platform.YOUTUBE) } returns PlatformConfigurationStatus(configured = true)
        val forwardedState = slot<String>()
        every {
            authorizationPort.buildAuthorizationUrl(
                Platform.YOUTUBE,
                "https://ongo.example.com/auth/channel-callback",
                capture(forwardedState),
                null,
            )
        } returns "https://accounts.google.com/o/oauth2/v2/auth?client_id=server-owned"

        val result = useCase().authorizationUrl(
            userId = 42L,
            platformValue = "youtube",
            redirectUri = "https://ongo.example.com/auth/channel-callback",
            state = "state-123",
            codeChallenge = null,
        )

        assertEquals("https://accounts.google.com/o/oauth2/v2/auth?client_id=server-owned", result.authorizationUrl)
        assertTrue(forwardedState.captured != "state-123")
        assertTrue(forwardedState.captured.contains('.'))
        verify(exactly = 1) {
            authorizationPort.buildAuthorizationUrl(
                Platform.YOUTUBE,
                "https://ongo.example.com/auth/channel-callback",
                capture(forwardedState),
                null,
            )
        }
    }

    @Test
    fun `unconfigured platform fails before an authorization URL can be built`() {
        every {
            configurationPort.status(Platform.TIKTOK)
        } returns PlatformConfigurationStatus(configured = false, reason = "TikTok 앱 키가 설정되지 않았습니다")

        val error = assertFailsWith<BusinessException> {
            useCase().authorizationUrl(
                userId = 42L,
                platformValue = "tiktok",
                redirectUri = "https://ongo.example.com/auth/channel-callback",
                state = "state-123",
                codeChallenge = null,
            )
        }

        assertEquals("PLATFORM_NOT_CONFIGURED", error.code)
        assertTrue(error.message!!.contains("TikTok"))
        verify(exactly = 0) { authorizationPort.buildAuthorizationUrl(any(), any(), any(), any()) }
    }

    @Test
    fun `redirect URI must be the configured callback origin`() {
        every { configurationPort.status(Platform.YOUTUBE) } returns PlatformConfigurationStatus(configured = true)

        assertFailsWith<IllegalArgumentException> {
            useCase().authorizationUrl(
                userId = 42L,
                platformValue = "youtube",
                redirectUri = "https://evil.example.com/auth/channel-callback",
                state = "state-123",
                codeChallenge = null,
            )
        }
        verify(exactly = 0) { authorizationPort.buildAuthorizationUrl(any(), any(), any(), any()) }
    }

    @Test
    fun `redirect URI rejects callback variations that could change its meaning`() {
        every { configurationPort.status(Platform.YOUTUBE) } returns PlatformConfigurationStatus(configured = true)

        listOf(
            "https://ongo.example.com/auth/channel-callback?next=https://evil.example.com",
            "https://ongo.example.com/auth/channel-callback#fragment",
            "https://user:pass@ongo.example.com/auth/channel-callback",
            "https://ongo.example.com/other-callback",
        ).forEach { redirectUri ->
            assertFailsWith<IllegalArgumentException> {
            useCase().authorizationUrl(42L, "youtube", redirectUri, "state-123", null)
            }
        }
        verify(exactly = 0) { authorizationPort.buildAuthorizationUrl(any(), any(), any(), any()) }
    }

    @Test
    fun `state length is bounded before the provider adapter is called`() {
        every { configurationPort.status(Platform.YOUTUBE) } returns PlatformConfigurationStatus(configured = true)

        assertFailsWith<IllegalArgumentException> {
            useCase().authorizationUrl(
                userId = 42L,
                platformValue = "youtube",
                redirectUri = "https://ongo.example.com/auth/channel-callback",
                state = "x".repeat(1025),
                codeChallenge = null,
            )
        }
        verify(exactly = 0) { authorizationPort.buildAuthorizationUrl(any(), any(), any(), any()) }
    }

    @Test
    fun `Naver Clip is rejected as an unsupported provider`() {
        every {
            configurationPort.status(Platform.NAVER_CLIP)
        } returns PlatformConfigurationStatus(configured = false, reason = "공개 API가 없습니다")

        val error = assertFailsWith<BusinessException> {
            useCase().authorizationUrl(
                userId = 42L,
                platformValue = "naver_clip",
                redirectUri = "https://ongo.example.com/auth/channel-callback",
                state = "state-123",
                codeChallenge = null,
            )
        }

        assertEquals("PLATFORM_NOT_CONFIGURED", error.code)
        verify(exactly = 0) { authorizationPort.buildAuthorizationUrl(any(), any(), any(), any()) }
    }

    @Test
    fun `Twitter requires PKCE challenge`() {
        every { configurationPort.status(Platform.TWITTER) } returns PlatformConfigurationStatus(configured = true)

        assertFailsWith<IllegalArgumentException> {
            useCase().authorizationUrl(
                userId = 42L,
                platformValue = "twitter",
                redirectUri = "http://localhost:5173/auth/channel-callback",
                state = "state-123",
                codeChallenge = null,
            )
        }
        verify(exactly = 0) { authorizationPort.buildAuthorizationUrl(any(), any(), any(), any()) }
    }
}
