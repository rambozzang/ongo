package com.ongo.application.channel

import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChannelOAuthStateManagerTest {
    private val secret = "test-oauth-state-secret-that-is-at-least-32-chars"
    private val redirectUri = "https://ongo.example.com/auth/channel-callback"

    @Test
    fun `발급한 state는 사용자 플랫폼 redirect URI에 바인딩되고 한 번만 소비된다`() {
        val manager = ChannelOAuthStateManager(secret)
        val state = manager.issue(42L, Platform.YOUTUBE, redirectUri, "YOUTUBE|/channels|nonce")

        manager.verifyAndConsume(state, 42L, Platform.YOUTUBE, redirectUri)

        val replay = assertFailsWith<BusinessException> {
            manager.verifyAndConsume(state, 42L, Platform.YOUTUBE, redirectUri)
        }
        assertEquals("OAUTH_STATE_INVALID", replay.code)
    }

    @Test
    fun `위조하거나 다른 사용자 플랫폼 redirect URI의 state는 거부한다`() {
        val manager = ChannelOAuthStateManager(secret)
        val state = manager.issue(42L, Platform.YOUTUBE, redirectUri, "YOUTUBE|/channels|nonce")

        listOf(
            { manager.verifyAndConsume("$state-tampered", 42L, Platform.YOUTUBE, redirectUri) },
            { manager.verifyAndConsume(state, 99L, Platform.YOUTUBE, redirectUri) },
            { manager.verifyAndConsume(state, 42L, Platform.TIKTOK, redirectUri) },
            { manager.verifyAndConsume(state, 42L, Platform.YOUTUBE, "https://evil.example.com/auth/channel-callback") },
        ).forEach { attempt ->
            val error = assertFailsWith<BusinessException> { attempt() }
            assertEquals("OAUTH_STATE_INVALID", error.code)
        }
    }

    @Test
    fun `TTL이 지난 state는 거부한다`() {
        val manager = ChannelOAuthStateManager(secret, ttlSeconds = 0)
        val state = manager.issue(42L, Platform.YOUTUBE, redirectUri, "YOUTUBE|/channels|nonce")
        Thread.sleep(1_100)

        val error = assertFailsWith<BusinessException> {
            manager.verifyAndConsume(state, 42L, Platform.YOUTUBE, redirectUri)
        }
        assertTrue(error.message!!.contains("검증"))
    }
}
