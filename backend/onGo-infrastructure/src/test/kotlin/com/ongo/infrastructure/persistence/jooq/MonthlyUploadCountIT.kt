package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.video.MonthlyUploadPolicy
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
import java.time.YearMonth

/**
 * 월 업로드 카운트가 **실제 PostgreSQL 에서** 원본만 세는지 고정한다.
 *
 * `videos.source` 는 PG enum(`video_source`)이다. 문자열 목록과 enum 을 그대로 비교하면
 * `operator does not exist: video_source = character varying` 으로 실패한다. 목으로는 이
 * 실패를 알 수 없어서 실DB 로 본다. 그리고 V116 의 DERIVED 값이 실제로 저장 가능한지도
 * 여기서 드러난다 — 값이 DB 에 없으면 INSERT 부터 죽는다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class MonthlyUploadCountIT {

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

        private const val EMAIL = "monthly-upload-count@test.io"
    }

    private var userId = 0L

    @BeforeEach
    fun setUp() {
        dsl.execute("DELETE FROM videos WHERE user_id IN (SELECT id FROM users WHERE email = ?)", EMAIL)
        dsl.execute("DELETE FROM users WHERE email = ?", EMAIL)
        userId = dsl.fetchOne(
            """
            INSERT INTO users (email, name, provider, provider_id, role, plan_type)
            VALUES (?, 'm', 'GOOGLE', 'monthly-upload-count', 'USER', 'FREE')
            RETURNING id
            """.trimIndent(),
            EMAIL,
        )!!.get(0, Long::class.javaObjectType)
    }

    private fun video(source: String, createdAt: LocalDateTime = LocalDateTime.now()) {
        dsl.execute(
            "INSERT INTO videos (user_id, title, source, created_at) VALUES (?, 't', CAST(? AS video_source), ?)",
            userId, source, createdAt,
        )
    }

    private fun count(month: YearMonth = YearMonth.now()) =
        videoRepository.countByUserIdAndMonthAndSources(userId, month, MonthlyUploadPolicy.COUNTED_SOURCES)

    @Test
    @DisplayName("원본 출처만 세고 쇼츠 결과물과 사본은 세지 않는다")
    fun countsOnlyOriginals() {
        video("UPLOAD_PC")
        video("GOOGLE_DRIVE")
        video("URL_IMPORT")
        repeat(20) { video("GENERATED") } // 쇼츠 한 번 돌린 결과
        video("DERIVED")                   // 재활용·반복 예약 사본

        assertEquals(3L, count(), "쇼츠 클립이나 사본이 섞이면 무료 사용자가 자기 영상을 못 올린다")
    }

    @Test
    @DisplayName("지난달 업로드는 이번 달 한도에 넣지 않는다")
    fun countsOnlyThisMonth() {
        val thisMonth = YearMonth.now()
        video("UPLOAD_PC", thisMonth.minusMonths(1).atEndOfMonth().atTime(23, 59, 59))
        video("UPLOAD_PC", thisMonth.atDay(1).atStartOfDay())

        assertEquals(1L, count(thisMonth))
    }

    /** V116 이 실제로 적용됐는지 — DERIVED 를 저장할 수 없으면 재활용·반복 예약이 INSERT 에서 죽는다. */
    @Test
    @DisplayName("DERIVED 출처를 저장할 수 있다")
    fun derivedSourceIsStorable() {
        video("DERIVED")
        val stored = dsl.fetchOne(
            "SELECT source::text FROM videos WHERE user_id = ? ORDER BY id DESC LIMIT 1", userId,
        )!!.get(0, String::class.java)
        assertEquals("DERIVED", stored)
    }
}
