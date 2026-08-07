package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.subtitleeditor.SubtitleEditorRepository
import com.ongo.domain.subtitleeditor.SubtitleTrack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class SubtitleEditorJooqRepositoryIT {

    @Autowired lateinit var repository: SubtitleEditorRepository
    @Autowired lateinit var dsl: org.jooq.DSLContext

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

    @BeforeEach
    fun clean() {
        dsl.deleteFrom(Tables.SUBTITLE_TRACKS).execute()
        dsl.execute(
            "INSERT INTO users (id, email, name, provider, provider_id, plan_type) " +
                "VALUES (901, 'subtitle@test.com', 'Subtitle', 'GOOGLE', 'subtitle-901', 'FREE') " +
                "ON CONFLICT (id) DO NOTHING",
        )
    }

    @Test
    fun `crud preserves JSON cues and ownership fields`() {
        val saved = repository.save(
            SubtitleTrack(
                userId = 901L,
                videoId = 77L,
                videoTitle = "테스트 영상",
                language = "ko",
                cues = "[{\"start\":0,\"end\":1.2,\"text\":\"안녕\"}]",
                totalDuration = BigDecimal("1.20"),
                wordCount = 1,
            ),
        )

        val loaded = repository.findById(saved.id!!)
        val savedId = requireNotNull(saved.id)
        assertEquals(saved.id, loaded?.id)
        assertEquals(901L, loaded?.userId)
        assertTrue(loaded?.cues?.contains("\"text\": \"안녕\"") == true)

        val updated = repository.update(loaded!!.copy(status = "READY", wordCount = 2))
        assertEquals("READY", updated.status)
        assertEquals(2, updated.wordCount)
        assertEquals(1, repository.findByUserId(901L).size)
        assertEquals(1, repository.findByVideoId(77L).size)

        repository.delete(savedId)
        assertTrue(repository.findById(savedId) == null)
    }
}
