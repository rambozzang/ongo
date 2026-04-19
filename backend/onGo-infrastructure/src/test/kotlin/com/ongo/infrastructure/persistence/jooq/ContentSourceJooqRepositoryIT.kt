package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentsource.ContentSource
import com.ongo.domain.contentsource.ContentSourceRepository
import com.ongo.domain.contentsource.ContentSourceStatus
import com.ongo.domain.contentsource.ContentSourceType
import com.ongo.infrastructure.persistence.jooq.Tables.USER_CONTENT_SOURCES
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.jooq.exception.IntegrityConstraintViolationException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ContentSourceJooqRepositoryIT {
    @Autowired lateinit var repo: ContentSourceRepository
    @Autowired lateinit var dsl: DSLContext

    companion object {
        @Container @JvmStatic
        val pg = PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("ongo_test")
            withUsername("test"); withPassword("test")
        }
        @JvmStatic @DynamicPropertySource
        fun props(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url") { pg.jdbcUrl }
            r.add("spring.datasource.username") { pg.username }
            r.add("spring.datasource.password") { pg.password }
        }
    }

    @BeforeEach
    fun cleanup() {
        dsl.deleteFrom(USER_CONTENT_SOURCES).execute()
        // users 외래키 조건 충족: 테스트 유저 보장
        dsl.execute(
            "INSERT INTO users (id, email, name, provider, provider_id, plan_type) " +
                "VALUES (100, 'u@test.com', 'T', 'GOOGLE', 'g100', 'FREE') ON CONFLICT (id) DO NOTHING"
        )
    }

    @Test
    fun `save and findByUserAndType roundtrip`() {
        val saved = repo.save(newActiveSource(userId = 100L))
        val found = repo.findByUserAndType(100L, ContentSourceType.GOOGLE_DRIVE)
        assertNotNull(found)
        assertEquals(saved.id, found!!.id)
        assertEquals("user@gmail.com", found.accountEmail)
        assertEquals(ContentSourceStatus.ACTIVE, found.status)
    }

    @Test
    fun `updateStatus marks expired`() {
        val saved = repo.save(newActiveSource(userId = 100L))
        repo.updateStatus(saved.id, ContentSourceStatus.EXPIRED, "invalid_grant")
        val found = repo.findById(saved.id)!!
        assertEquals(ContentSourceStatus.EXPIRED, found.status)
        assertEquals("invalid_grant", found.lastError)
    }

    @Test
    fun `unique constraint on user and source type`() {
        repo.save(newActiveSource(userId = 100L))
        assertThrows(IntegrityConstraintViolationException::class.java) {
            repo.save(newActiveSource(userId = 100L))
        }
    }

    private fun newActiveSource(userId: Long) = ContentSource(
        id = 0L, userId = userId, sourceType = ContentSourceType.GOOGLE_DRIVE,
        externalAccountId = "google-sub-${userId}", accountEmail = "user@gmail.com",
        accountDisplayName = "User", accessTokenEncrypted = "enc:at",
        refreshTokenEncrypted = "enc:rt", tokenExpiresAt = Instant.now().plusSeconds(3600),
        grantedScopes = "drive.readonly", status = ContentSourceStatus.ACTIVE,
        lastError = null, connectedAt = Instant.now(), lastUsedAt = null, updatedAt = Instant.now(),
    )
}
