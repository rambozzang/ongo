package com.ongo.infrastructure.external.googledrive

import com.ongo.domain.contentsource.exception.OAuthStateMismatchException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class OAuthStateManagerTest {
    private val secret = "test-secret-32-chars-minimum-abc"

    @Test
    fun `issue and verify roundtrip returns userId`() {
        val manager = OAuthStateManager(secret, ttlSeconds = 300)
        val state = manager.issue(userId = 42L)
        assertEquals(42L, manager.verify(state))
    }

    @Test
    fun `a state can only be consumed once`() {
        val manager = OAuthStateManager(secret, ttlSeconds = 300)
        val state = manager.issue(userId = 42L)

        assertEquals(42L, manager.verify(state))
        assertThrows(OAuthStateMismatchException::class.java) { manager.verify(state) }
    }

    @Test
    fun `anonymous login state can only be consumed once`() {
        val manager = OAuthStateManager(secret, ttlSeconds = 300)
        val state = manager.issueAnonymous()

        assertEquals(true, manager.verifyAnonymous(state))
        assertEquals(false, manager.verifyAnonymous(state))
    }

    @Test
    fun `one-time consumption is delegated to the configured shared store`() {
        val store = mockk<OAuthStateStore>()
        every { store.consumeOnce(any(), 300) } returnsMany listOf(true, false)
        val manager = OAuthStateManager(secret, ttlSeconds = 300, stateStore = store)
        val state = manager.issueAnonymous()

        assertEquals(true, manager.verifyAnonymous(state))
        assertEquals(false, manager.verifyAnonymous(state))
        verify(exactly = 2) { store.consumeOnce(state, 300) }
    }

    @Test
    fun `verify throws on tampered state`() {
        val manager = OAuthStateManager(secret, ttlSeconds = 300)
        val state = manager.issue(userId = 42L)
        val tampered = state.substringBeforeLast('.') + ".tamperedSignatureBytes"
        assertThrows(OAuthStateMismatchException::class.java) { manager.verify(tampered) }
    }

    @Test
    fun `verify throws when expired`() {
        val manager = OAuthStateManager(secret, ttlSeconds = 0)
        val state = manager.issue(userId = 42L)
        Thread.sleep(1_100)  // ensure > ttlSeconds wall clock elapsed
        assertThrows(OAuthStateMismatchException::class.java) { manager.verify(state) }
    }

    @Test
    fun `verify throws on malformed state (no dot separator)`() {
        val manager = OAuthStateManager(secret, ttlSeconds = 300)
        assertThrows(OAuthStateMismatchException::class.java) { manager.verify("not-a-valid-state") }
    }

    @Test
    fun `verify throws on wrong secret`() {
        val issuer = OAuthStateManager(secret, ttlSeconds = 300)
        val state = issuer.issue(userId = 42L)
        val verifier = OAuthStateManager("different-secret-32-chars-aaaaaa", ttlSeconds = 300)
        assertThrows(OAuthStateMismatchException::class.java) { verifier.verify(state) }
    }
}
