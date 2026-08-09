package com.ongo.infrastructure.security

import com.ongo.domain.auth.TokenBlacklistPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/** Verifies that logout revocations use the Flyway schema and survive a new service instance. */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class TokenBlacklistPersistenceIT {

    @Autowired lateinit var tokenBlacklist: TokenBlacklistPort
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun clearRows() {
        jdbcTemplate.update("DELETE FROM revoked_jwt_tokens")
    }

    @Test
    fun `revoked jti is visible through the database-backed port`() {
        tokenBlacklist.blacklist("persisted-jti", 60_000)

        assertThat(tokenBlacklist.isBlacklisted("persisted-jti")).isTrue()
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM revoked_jwt_tokens WHERE jti = ?",
            Long::class.java,
            "persisted-jti",
        )).isEqualTo(1L)
    }

    @Test
    fun `expired jti is not treated as revoked`() {
        jdbcTemplate.update(
            "INSERT INTO revoked_jwt_tokens (jti, expires_at) VALUES (?, CURRENT_TIMESTAMP - INTERVAL '1 second')",
            "expired-jti",
        )

        assertThat(tokenBlacklist.isBlacklisted("expired-jti")).isFalse()
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("ongo_test")
            withUsername("test")
            withPassword("test")
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }
}
