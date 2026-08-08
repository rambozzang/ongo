package com.ongo.infrastructure.contentsource

import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.contentsource.ContentSource
import com.ongo.domain.contentsource.ContentSourceRepository
import com.ongo.domain.contentsource.ContentSourceStatus
import com.ongo.domain.contentsource.ContentSourceType
import com.ongo.domain.contentsource.exception.ContentSourceExpiredException
import com.ongo.domain.contentsource.exception.ContentSourceNotConnectedException
import com.ongo.infrastructure.external.googledrive.GoogleDriveOAuthClient
import com.ongo.infrastructure.external.googledrive.OAuthTokenExchangeException
import com.ongo.infrastructure.external.googledrive.dto.TokenResponse
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ContentSourceTokenManagerTest {
    private val repo = mockk<ContentSourceRepository>(relaxed = true)
    private val oauth = mockk<GoogleDriveOAuthClient>()
    private val encryptor = mockk<TokenEncryptionPort>()

    private lateinit var manager: ContentSourceTokenManager

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        manager = ContentSourceTokenManager(repo, oauth, encryptor)
        every { encryptor.decrypt(any()) } answers { PlainToken(firstArg<EncryptedToken>().value.removePrefix("enc:")) }
        every { encryptor.encrypt(any()) } answers { EncryptedToken("enc:${firstArg<PlainToken>().value}") }
    }

    @Test
    fun `ensureValidToken returns decrypted access token when not expired`() {
        every { repo.findById(1) } returns sampleSource(
            id = 1, tokenExpiresAt = Instant.now().plusSeconds(3600),
            accessTokenEncrypted = "enc:at1",
        )
        val token = manager.ensureValidToken(sourceId = 1)
        assertEquals("at1", token)
        verify(exactly = 0) { oauth.refresh(any()) }
    }

    @Test
    fun `ensureValidToken refreshes when expired`() {
        every { repo.findById(1) } returns sampleSource(
            id = 1, tokenExpiresAt = Instant.now().minusSeconds(10),
            accessTokenEncrypted = "enc:at-old", refreshTokenEncrypted = "enc:rt",
        )
        every { oauth.refresh("rt") } returns TokenResponse(
            accessToken = "at-new", refreshToken = null, expiresIn = 3600,
            idToken = null, scope = "drive.readonly", tokenType = "Bearer",
        )

        val token = manager.ensureValidToken(1)

        assertEquals("at-new", token)
        verify { repo.updateTokens(1, "enc:at-new", null, any()) }
    }

    @Test
    fun `ensureValidToken marks source EXPIRED and throws when refresh fails`() {
        every { repo.findById(1) } returns sampleSource(
            id = 1, tokenExpiresAt = Instant.now().minusSeconds(10),
            accessTokenEncrypted = "enc:at", refreshTokenEncrypted = "enc:rt",
        )
        every { oauth.refresh("rt") } throws OAuthTokenExchangeException("invalid_grant")

        assertThrows(ContentSourceExpiredException::class.java) {
            manager.ensureValidToken(1)
        }
        verify { repo.updateStatus(1, ContentSourceStatus.EXPIRED, match { it!!.contains("invalid_grant") }) }
    }

    @Test
    fun `ensureValidToken throws when source not found`() {
        every { repo.findById(999) } returns null
        assertThrows(ContentSourceNotConnectedException::class.java) {
            manager.ensureValidToken(999)
        }
    }

    @Test
    fun `ensureValidToken throws when source status is EXPIRED`() {
        every { repo.findById(1) } returns sampleSource(id = 1, status = ContentSourceStatus.EXPIRED)
        assertThrows(ContentSourceExpiredException::class.java) {
            manager.ensureValidToken(1)
        }
    }

    @Test
    fun `concurrent ensureValidToken calls serialize refresh — at most one refresh per source`() {
        val expiredSource = sampleSource(
            id = 1, tokenExpiresAt = Instant.now().minusSeconds(10),
            accessTokenEncrypted = "enc:at-old", refreshTokenEncrypted = "enc:rt",
        )
        val refreshedSource = sampleSource(
            id = 1, tokenExpiresAt = Instant.now().plusSeconds(3600),
            accessTokenEncrypted = "enc:at-new", refreshTokenEncrypted = "enc:rt",
        )
        // 첫 호출은 expired, 이후 호출은 refreshed
        every { repo.findById(1) } returnsMany listOf(expiredSource) + List(10) { refreshedSource }
        every { oauth.refresh("rt") } returns TokenResponse(
            accessToken = "at-new", refreshToken = null, expiresIn = 3600,
            idToken = null, scope = null, tokenType = "Bearer",
        )

        val pool = Executors.newFixedThreadPool(5)
        val futures = (1..5).map { pool.submit { manager.ensureValidToken(1) } }
        val results = futures.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(5, results.size)
        // refresh는 최대 1회만 호출
        verify(atMost = 1) { oauth.refresh(any()) }
    }

    private fun sampleSource(
        id: Long = 1,
        status: ContentSourceStatus = ContentSourceStatus.ACTIVE,
        tokenExpiresAt: Instant? = Instant.now().plusSeconds(3600),
        accessTokenEncrypted: String = "enc:at",
        refreshTokenEncrypted: String? = "enc:rt",
    ) = ContentSource(
        id = id, userId = 100L, sourceType = ContentSourceType.GOOGLE_DRIVE,
        externalAccountId = "google-sub-$id", accountEmail = "u@gmail.com",
        accountDisplayName = "U", accessTokenEncrypted = accessTokenEncrypted,
        refreshTokenEncrypted = refreshTokenEncrypted, tokenExpiresAt = tokenExpiresAt,
        grantedScopes = "drive.readonly", status = status, lastError = null,
        connectedAt = Instant.now(), lastUsedAt = null, updatedAt = Instant.now(),
    )
}
