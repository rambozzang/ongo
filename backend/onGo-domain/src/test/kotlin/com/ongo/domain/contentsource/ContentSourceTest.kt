package com.ongo.domain.contentsource

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentSourceTest {
    @Test
    fun `needsRefresh returns true when token expires within 60 seconds`() {
        val source = sampleSource(tokenExpiresAt = Instant.now().plusSeconds(30))
        assertTrue(source.needsRefresh(Instant.now()))
    }

    @Test
    fun `needsRefresh returns false when token expires more than 60 seconds later`() {
        val source = sampleSource(tokenExpiresAt = Instant.now().plusSeconds(600))
        assertFalse(source.needsRefresh(Instant.now()))
    }

    @Test
    fun `markExpired sets status to EXPIRED`() {
        val source = sampleSource(status = ContentSourceStatus.ACTIVE)
        val expired = source.markExpired("invalid_grant")
        assertEquals(ContentSourceStatus.EXPIRED, expired.status)
        assertEquals("invalid_grant", expired.lastError)
    }

    private fun sampleSource(
        status: ContentSourceStatus = ContentSourceStatus.ACTIVE,
        tokenExpiresAt: Instant? = Instant.now().plusSeconds(3600),
    ) = ContentSource(
        id = 1L,
        userId = 100L,
        sourceType = ContentSourceType.GOOGLE_DRIVE,
        externalAccountId = "google-sub-123",
        accountEmail = "user@gmail.com",
        accountDisplayName = "User",
        accessTokenEncrypted = "enc",
        refreshTokenEncrypted = "enc",
        tokenExpiresAt = tokenExpiresAt,
        grantedScopes = "drive.readonly",
        status = status,
        lastError = null,
        connectedAt = Instant.now(),
        lastUsedAt = null,
        updatedAt = Instant.now(),
    )
}
