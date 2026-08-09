package com.ongo.infrastructure.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

class TokenBlacklistServiceTest {

    private val jdbcTemplate = mockk<JdbcTemplate>(relaxed = true)
    private val service = TokenBlacklistService(jdbcTemplate)

    @Test
    fun `logout stores only the jti and its expiry`() {
        service.blacklist("jti-1", 60_000)

        verify {
            jdbcTemplate.update(
                match { it.contains("revoked_jwt_tokens") && it.contains("ON CONFLICT") },
                "jti-1",
                any(),
            )
        }
    }

    @Test
    fun `blacklist lookup delegates to an expiry-aware database query`() {
        every { jdbcTemplate.queryForObject(any<String>(), Boolean::class.java, "jti-1") } returns true

        assertThat(service.isBlacklisted("jti-1")).isTrue()
        verify {
            jdbcTemplate.queryForObject(
                match { it.contains("expires_at > CURRENT_TIMESTAMP") },
                Boolean::class.java,
                "jti-1",
            )
        }
    }

    @Test
    fun `expired rows are periodically removed`() {
        every { jdbcTemplate.update(any<String>()) } returns 2

        service.purgeExpired()

        verify { jdbcTemplate.update(match<String> { it.contains("DELETE FROM revoked_jwt_tokens") }) }
    }

    @Test
    fun `blank jti cannot be persisted and is never considered revoked`() {
        assertThat(service.isBlacklisted(" ")).isFalse()
        verify(exactly = 0) { jdbcTemplate.queryForObject(any<String>(), Boolean::class.java, any()) }
    }
}
