package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.video.VideoRepository
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime

/**
 * 방치 업로드 정리가 **진행 중인 긴 업로드를 지우지 않는지** 실 PostgreSQL 에서 고정한다.
 *
 * 10GB 원본은 느린 회선에서 3시간을 넘는다. 생성 시각만 보던 때는 그런 업로드를 올리는 도중에
 * 오브젝트째 지웠다. 조각 URL 을 받을 때마다 남기는 활동 시각으로 판정한다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class StaleUploadActivityIT {

    @Autowired lateinit var videoRepository: VideoRepository
    @Autowired lateinit var dsl: DSLContext

    companion object {
        @Container @JvmStatic
        val pg = PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("ongo_test"); withUsername("test"); withPassword("test")
        }

        @JvmStatic @DynamicPropertySource
        fun props(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url") { pg.jdbcUrl }
            r.add("spring.datasource.username") { pg.username }
            r.add("spring.datasource.password") { pg.password }
        }
    }

    private val now = LocalDateTime.of(2026, 9, 23, 12, 0)
    private val threshold = now.minusMinutes(180)
    private var userId = 0L

    @BeforeEach
    fun setUp() {
        dsl.execute("DELETE FROM videos WHERE user_id IN (SELECT id FROM users WHERE email = 'stale-it@test.io')")
        dsl.execute("DELETE FROM users WHERE email = 'stale-it@test.io'")
        userId = dsl.fetchOne(
            "INSERT INTO users (email, name, provider, provider_id, role) VALUES ('stale-it@test.io','s','GOOGLE','stale-it','USER') RETURNING id",
        )!!.get(0, Long::class.javaObjectType)
    }

    private fun uploading(createdAt: LocalDateTime, updatedAt: LocalDateTime, status: String = "UPLOADING"): Long =
        dsl.fetchOne(
            "INSERT INTO videos (user_id, title, status, created_at, updated_at) " +
                "VALUES (?, 't', CAST(? AS video_status), ?, ?) RETURNING id",
            userId, status, createdAt, updatedAt,
        )!!.get(0, Long::class.javaObjectType)

    private fun staleIds() = videoRepository.findStaleUploading(threshold, 100).mapNotNull { it.id }.toSet()

    @Test
    @DisplayName("오래전에 시작했어도 최근에 조각을 받은 업로드는 지우지 않는다")
    fun activeLongUploadIsKept() {
        val abandoned = uploading(createdAt = now.minusHours(5), updatedAt = now.minusHours(5))
        val stillUploading = uploading(createdAt = now.minusHours(5), updatedAt = now.minusMinutes(10))
        uploading(createdAt = now.minusMinutes(30), updatedAt = now.minusMinutes(30))

        assertEquals(setOf(abandoned), staleIds())
        assertEquals(false, stillUploading in staleIds())
    }

    @Test
    @DisplayName("활동을 남기면 방치 대상에서 빠지고, 업로드 중이 아닌 행은 건드리지 않는다")
    fun touchKeepsItAlive() {
        val id = uploading(createdAt = now.minusHours(5), updatedAt = now.minusHours(5))
        val draft = uploading(createdAt = now.minusHours(5), updatedAt = now.minusHours(5), status = "DRAFT")

        videoRepository.touchUploadActivity(id, now.minusMinutes(1))
        videoRepository.touchUploadActivity(draft, now.minusMinutes(1))

        assertEquals(emptySet<Long>(), staleIds())
        val draftUpdated = dsl.fetchOne("SELECT updated_at FROM videos WHERE id = ?", draft)!!.get(0, LocalDateTime::class.java)
        assertEquals(now.minusHours(5), draftUpdated, "업로드 중이 아닌 행의 시각을 바꿨다")
    }
}
