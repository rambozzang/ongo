package com.ongo.application.auth

import com.ongo.domain.auth.AuthOAuthAuthorizationPort
import com.ongo.domain.auth.AuthOAuthProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthOAuthAuthorizationUseCaseTest {
    private val authorizationPort = mockk<AuthOAuthAuthorizationPort>()

    private fun useCase() = AuthOAuthAuthorizationUseCase(
        authorizationPort = authorizationPort,
        allowedOrigins = "https://ongo.example.com,http://localhost:5173",
    )

    @Test
    fun `server returns the provider URL for a valid login callback`() {
        every {
            authorizationPort.buildAuthorizationUrl(
                AuthOAuthProvider.KAKAO,
                "https://ongo.example.com/auth/callback/kakao",
                "state-123",
            )
        } returns "https://kauth.kakao.com/oauth/authorize?client_id=server-owned"

        assertEquals(
            "https://kauth.kakao.com/oauth/authorize?client_id=server-owned",
            useCase().authorizationUrl(
                "kakao",
                "https://ongo.example.com/auth/callback/kakao",
                "state-123",
            ),
        )
    }

    @Test
    fun `unknown provider is rejected before the adapter is called`() {
        assertFailsWith<com.ongo.common.exception.BusinessException> {
            useCase().authorizationUrl("naver", "https://ongo.example.com/auth/callback/naver", "state-123")
        }
        verify(exactly = 0) { authorizationPort.buildAuthorizationUrl(any(), any(), any()) }
    }

    @Test
    fun `login callback must use the allowlisted origin and provider path`() {
        assertFailsWith<IllegalArgumentException> {
            useCase().authorizationUrl(
                "google",
                "https://evil.example.com/auth/callback/google",
                "state-123",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            useCase().authorizationUrl(
                "google",
                "https://ongo.example.com/auth/callback/kakao",
                "state-123",
            )
        }
        verify(exactly = 0) { authorizationPort.buildAuthorizationUrl(any(), any(), any()) }
    }
}
