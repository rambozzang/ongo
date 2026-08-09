package com.ongo.application.publicoauth

import com.ongo.common.exception.ForbiddenException
import com.ongo.domain.publicoauth.PublicOAuthApp
import com.ongo.domain.publicoauth.PublicOAuthAppRepository
import com.ongo.domain.publicoauth.PublicOAuthAuthorizationCode
import com.ongo.domain.publicoauth.PublicOAuthAuthorizationCodeRepository
import com.ongo.domain.publicoauth.PublicOAuthToken
import com.ongo.domain.publicoauth.PublicOAuthTokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

class PublicOAuthAppUseCaseTest {
    private val apps = InMemoryApps()
    private val codes = InMemoryCodes()
    private val tokens = InMemoryTokens()
    private val useCase = PublicOAuthAppUseCase(apps, codes, tokens)

    @Test
    fun `create stores only a secret hash and validates production redirect uri`() {
        val created = useCase.createApp(
            ownerId = 7L,
            request = CreatePublicOAuthAppRequest(
                name = "Automation client",
                description = "Posts from our scheduler",
                redirectUri = "https://client.example.com/oauth/callback",
            ),
        )

        assertThat(created.app.clientId).startsWith("pca_")
        assertThat(created.clientSecret).startsWith("pcs_")
        assertThat(apps.saved!!.clientSecretHash)
            .isNotEqualTo(created.clientSecret)
            .isEqualTo(sha256(created.clientSecret))

        assertThatIllegalArgumentException().isThrownBy {
            useCase.createApp(
                ownerId = 7L,
                request = CreatePublicOAuthAppRequest(
                    name = "Unsafe",
                    redirectUri = "http://attacker.example.com/callback",
                ),
            )
        }
    }

    @Test
    fun `approval preserves state and authorization code can be exchanged only once`() {
        val created = createApp()

        val redirect = useCase.decideAuthorization(
            userId = 42L,
            clientId = created.app.clientId,
            responseType = "code",
            state = "state with spaces",
            approved = true,
        )
        assertThat(redirect).startsWith("https://client.example.com/oauth/callback?")
        assertThat(redirect).contains("state=state%20with%20spaces")
        assertThat(redirect).contains("code=")

        val code = codes.saved!!
        assertThat(code.codeHash).isNotBlank()

        // The raw code is intentionally never exposed by the repository. Seed a
        // second known code to exercise the token exchange without weakening that
        // one-way storage contract.
        val rawCode = "known-one-time-code"
        codes.saved = PublicOAuthAuthorizationCode(
            appId = created.app.id!!,
            userId = 42L,
            codeHash = sha256(rawCode),
            redirectUri = created.app.redirectUri,
            expiresAt = LocalDateTime.now().plusMinutes(5),
        )
        codes.consumed = false
        val token = useCase.exchangeToken(
            PublicOAuthTokenRequest(
                grantType = "authorization_code",
                code = rawCode,
                clientId = created.app.clientId,
                clientSecret = created.clientSecret,
            ),
        )

        assertThat(token.id).isEqualTo("42")
        assertThat(token.accessToken).startsWith("pos_")
        assertThat(tokens.saved!!.tokenHash).isEqualTo(sha256(token.accessToken))
        assertThat(tokens.saved!!.tokenHash).doesNotContain(token.accessToken)
        assertThat(codes.consumed).isTrue()

        assertThatIllegalArgumentException().isThrownBy {
            useCase.exchangeToken(
                PublicOAuthTokenRequest(
                    grantType = "authorization_code",
                    code = rawCode,
                    clientId = created.app.clientId,
                    clientSecret = created.clientSecret,
                ),
            )
        }.withMessage("invalid_grant")
    }

    @Test
    fun `denial returns validated redirect without creating a code`() {
        val created = createApp()

        val redirect = useCase.decideAuthorization(
            userId = 42L,
            clientId = created.app.clientId,
            responseType = "code",
            state = "csrf-token",
            approved = false,
        )

        assertThat(redirect)
            .isEqualTo("https://client.example.com/oauth/callback?error=access_denied&state=csrf-token")
        assertThat(codes.saved).isNull()
    }

    @Test
    fun `rotating a secret invalidates the previous secret and ownership is enforced`() {
        val created = createApp()
        val oldSecret = created.clientSecret
        val rotated = useCase.rotateSecret(ownerId = 7L, id = created.app.id!!)

        assertThat(rotated.clientSecret).isNotEqualTo(oldSecret)
        assertThatIllegalArgumentException().isThrownBy {
            useCase.exchangeToken(
                PublicOAuthTokenRequest("authorization_code", "unused", created.app.clientId, oldSecret),
            )
        }.withMessage("invalid_client")

        assertThatThrownByForbidden {
            useCase.rotateSecret(ownerId = 999L, id = created.app.id)
        }
    }

    private fun createApp(): PublicOAuthAppCreatedResponse =
        useCase.createApp(
            ownerId = 7L,
            request = CreatePublicOAuthAppRequest(
                name = "Automation client",
                redirectUri = "https://client.example.com/oauth/callback",
            ),
        )

    private fun assertThatThrownByForbidden(block: () -> Unit) {
        try {
            block()
        } catch (_: ForbiddenException) {
            return
        }
        error("Expected ForbiddenException")
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private class InMemoryApps : PublicOAuthAppRepository {
        private var nextId = 1L
        private val values = mutableListOf<PublicOAuthApp>()
        var saved: PublicOAuthApp? = null

        override fun findById(id: Long) = values.find { it.id == id }
        override fun findByClientId(clientId: String) = values.find { it.clientId == clientId }
        override fun findByOwnerId(ownerId: Long) = values.filter { it.ownerId == ownerId }
        override fun save(app: PublicOAuthApp): PublicOAuthApp {
            val stored = app.copy(id = nextId++, createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())
            values += stored
            saved = stored
            return stored
        }
        override fun update(app: PublicOAuthApp): PublicOAuthApp {
            values.replaceAll { if (it.id == app.id) app else it }
            saved = app
            return app
        }
        override fun rotateSecret(id: Long, secretHash: String, updatedAt: LocalDateTime): Boolean {
            val current = findById(id) ?: return false
            update(current.copy(clientSecretHash = secretHash, updatedAt = updatedAt))
            return true
        }
        override fun revoke(id: Long, ownerId: Long, revokedAt: LocalDateTime): Boolean = false
    }

    private class InMemoryCodes : PublicOAuthAuthorizationCodeRepository {
        var saved: PublicOAuthAuthorizationCode? = null
        var consumed = false

        override fun save(code: PublicOAuthAuthorizationCode): PublicOAuthAuthorizationCode {
            saved = code
            return code
        }

        override fun consume(codeHash: String, now: LocalDateTime): PublicOAuthAuthorizationCode? {
            if (consumed || saved?.codeHash != codeHash || saved!!.expiresAt <= now) return null
            consumed = true
            return saved
        }
    }

    private class InMemoryTokens : PublicOAuthTokenRepository {
        var saved: PublicOAuthToken? = null
        override fun findActiveByHash(tokenHash: String) = saved?.takeIf { it.tokenHash == tokenHash && it.revokedAt == null }
        override fun save(token: PublicOAuthToken): PublicOAuthToken {
            saved = token.copy(id = 1L, createdAt = LocalDateTime.now())
            return saved!!
        }
        override fun revokeByApp(appId: Long, revokedAt: LocalDateTime) = 0
        override fun revokeByIdAndUser(id: Long, userId: Long, revokedAt: LocalDateTime) = false
        override fun findByUserId(userId: Long) = listOfNotNull(saved?.takeIf { it.userId == userId })
    }
}
