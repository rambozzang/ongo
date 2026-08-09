package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.publicapi.PublicApiPost
import com.ongo.domain.publicapi.PublicApiPostRepository
import com.ongo.domain.publicapi.PublicApiPostStatus
import com.ongo.domain.publicapi.PublicApiPostType
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class PublicApiPostJooqRepositoryIT {
    @Autowired lateinit var repo: PublicApiPostRepository
    @Autowired lateinit var dsl: DSLContext

    companion object {
        private const val USER_ID = 991L
        private const val VIDEO_ID = 991L

        @Container @JvmStatic
        val pg = PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("ongo_test")
            withUsername("test")
            withPassword("test")
        }

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { pg.jdbcUrl }
            registry.add("spring.datasource.username") { pg.username }
            registry.add("spring.datasource.password") { pg.password }
        }
    }

    @AfterEach
    fun cleanup() {
        dsl.execute("DELETE FROM users WHERE id = $USER_ID")
    }

    @Test
    fun `same user and idempotency key returns the original post`() {
        dsl.execute(
            "INSERT INTO users (id, email, name, provider, provider_id) " +
                "VALUES ($USER_ID, 'public-api-$USER_ID@test.com', 'Public API', 'GOOGLE', 'public-api-$USER_ID')",
        )
        dsl.execute(
            "INSERT INTO videos (id, user_id, title, status) " +
                "VALUES ($VIDEO_ID, $USER_ID, 'Public API video', 'DRAFT')",
        )

        val request = PublicApiPost(
            userId = USER_ID,
            videoId = VIDEO_ID,
            type = PublicApiPostType.NOW,
            status = PublicApiPostStatus.PROCESSING,
            payloadJson = "{}",
            idempotencyKey = "retry-$USER_ID",
            requestHash = "hash-1",
        )

        val first = repo.save(request)
        val second = repo.save(request.copy(payloadJson = "different payload"))

        assertEquals(first.id, second.id)
        assertEquals("{}", second.payloadJson)
        assertEquals("hash-1", second.requestHash)
        assertEquals(first.id, repo.findByUserIdAndIdempotencyKey(USER_ID, "retry-$USER_ID")?.id)
    }
}
