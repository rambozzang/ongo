package com.ongo.infrastructure.security.oauth

import com.ongo.common.exception.BusinessException
import com.ongo.domain.auth.AuthOAuthProvider
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthOAuthAuthorizationAdapterTest {
    private fun adapter(
        googleClientId: String = "google-client-id",
        kakaoClientId: String = "kakao-client-id",
    ) = AuthOAuthAuthorizationAdapter(googleClientId, kakaoClientId)

    @Test
    fun `Google URL uses the server client id and login scopes`() {
        val url = URI.create(
            adapter().buildAuthorizationUrl(
                AuthOAuthProvider.GOOGLE,
                "https://ongo.example.com/auth/callback/google",
                "state-123",
            ),
        )
        val query = url.rawQuery

        assertEquals("accounts.google.com", url.host)
        assertTrue(query.contains("client_id=google-client-id"))
        assertTrue(query.contains("scope=openid%20email%20profile"))
        assertTrue(query.contains("state=state-123"))
    }

    @Test
    fun `missing Kakao client id fails closed`() {
        val error = assertFailsWith<BusinessException> {
            adapter(kakaoClientId = "your-client-id").buildAuthorizationUrl(
                AuthOAuthProvider.KAKAO,
                "https://ongo.example.com/auth/callback/kakao",
                "state-123",
            )
        }

        assertEquals("OAUTH_NOT_CONFIGURED", error.code)
    }
}
