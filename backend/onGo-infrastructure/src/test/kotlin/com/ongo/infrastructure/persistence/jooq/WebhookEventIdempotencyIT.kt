package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import com.ongo.infrastructure.persistence.jooq.Fields.EVENT_ID
import com.ongo.infrastructure.persistence.jooq.Fields.PROCESSED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Tables.WEBHOOK_EVENTS
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 웹훅 멱등 게이트를 **실제 PostgreSQL**에서 검증한다.
 *
 * 단위 테스트(`WebhookEventJooqRepositoryTest`)는 jOOQ가 `ON CONFLICT DO NOTHING` SQL을
 * 발행한다는 것까지만 증명한다. 그 SQL이 실제 동시 실행에서 정말 한 쪽만 통과시키는지,
 * `VARCHAR(200)` 경계에서 어떻게 동작하는지는 DB가 있어야 알 수 있다.
 *
 * 유료 파일럿 전 릴리스 게이트다. 여기서 실패하면 크레딧이 중복 지급된다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class WebhookEventIdempotencyIT {

    @Autowired lateinit var repo: WebhookEventRepository
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

        /** `webhook_events.event_id` 컬럼 길이. */
        private const val EVENT_ID_COLUMN_LENGTH = 200
    }

    @BeforeEach
    fun cleanup() {
        dsl.deleteFrom(WEBHOOK_EVENTS).execute()
    }

    private fun event(eventId: String) = WebhookEvent(
        eventId = eventId,
        eventType = "Transaction.Paid",
        payload = """{"type":"Transaction.Paid"}""",
    )

    @Test
    @DisplayName("같은 event_id를 동시에 넣으면 정확히 하나만 성공한다 — 중복 크레딧 지급의 최종 방어선")
    fun concurrentInsertsAllowExactlyOne() {
        val eventId = "portone:concurrent-webhook"
        val workers = 8
        val pool = Executors.newFixedThreadPool(workers)
        val startLine = CountDownLatch(1)

        try {
            val tasks = (1..workers).map {
                Callable {
                    // 모든 스레드를 같은 순간에 출발시켜 경합을 실제로 만든다
                    startLine.await()
                    repo.saveIfAbsent(event(eventId))
                }
            }
            val futures = tasks.map { pool.submit(it) }
            startLine.countDown()
            val results = futures.map { it.get(30, TimeUnit.SECONDS) }

            assertEquals(1, results.count { it }, "정확히 한 스레드만 true를 받아야 한다: $results")
            assertEquals(workers - 1, results.count { !it }, "나머지는 전부 중복으로 걸러져야 한다")
            assertEquals(
                1,
                dsl.fetchCount(WEBHOOK_EVENTS, EVENT_ID.eq(eventId)),
                "행이 하나만 남아야 한다",
            )
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    @DisplayName("서로 다른 event_id는 동시에 넣어도 모두 성공한다 — 게이트가 정상 웹훅을 막지 않는다")
    fun concurrentDistinctInsertsAllSucceed() {
        val workers = 8
        val pool = Executors.newFixedThreadPool(workers)
        val startLine = CountDownLatch(1)

        try {
            val futures = (1..workers).map { n ->
                pool.submit(Callable { startLine.await(); repo.saveIfAbsent(event("portone:distinct-$n")) })
            }
            startLine.countDown()
            val results = futures.map { it.get(30, TimeUnit.SECONDS) }

            assertTrue(results.all { it }, "서로 다른 키는 전부 삽입돼야 한다: $results")
            assertEquals(workers, dsl.fetchCount(WEBHOOK_EVENTS))
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    @DisplayName("해시 폴백 키(79자)가 실제로 삽입된다")
    fun hashFallbackKeyInserts() {
        val digest = MessageDigest.getInstance("SHA-256").digest("w".repeat(500).toByteArray())
        val key = "portone:sha256:" + digest.joinToString("") { "%02x".format(it) }

        assertEquals(79, key.length, "폴백 키 길이가 바뀌면 이 테스트를 갱신해야 한다")
        assertTrue(repo.saveIfAbsent(event(key)))
        assertEquals(1, dsl.fetchCount(WEBHOOK_EVENTS, EVENT_ID.eq(key)))
    }

    @Test
    @DisplayName("event_id는 200자까지 들어가고 201자는 거부된다 — 해시 폴백이 필요한 이유")
    fun eventIdLengthBoundary() {
        val exactly200 = "p".repeat(EVENT_ID_COLUMN_LENGTH)
        assertTrue(repo.saveIfAbsent(event(exactly200)), "경계값 200자는 들어가야 한다")

        val over = "q".repeat(EVENT_ID_COLUMN_LENGTH + 1)
        // 잘라내기 대신 해시 폴백을 쓰는 근거다. 그냥 넣으면 여기서 터진다.
        assertThrows(Exception::class.java) { repo.saveIfAbsent(event(over)) }
    }

    @Test
    @DisplayName("markProcessed가 실제로 status와 processed_at을 바꾼다")
    fun markProcessedUpdatesRow() {
        val eventId = "portone:mark-me"
        assertTrue(repo.saveIfAbsent(event(eventId)))

        val before = dsl.select().from(WEBHOOK_EVENTS).where(EVENT_ID.eq(eventId)).fetchOne()!!
        assertEquals("PENDING", before.get(STATUS))

        val processedAt = LocalDateTime.now()
        assertTrue(repo.markProcessed(eventId, processedAt))

        val after = dsl.select().from(WEBHOOK_EVENTS).where(EVENT_ID.eq(eventId)).fetchOne()!!
        assertEquals("PROCESSED", after.get(STATUS))
        assertNotNull(after.get(PROCESSED_AT), "processed_at이 기록돼야 한다")
    }

    @Test
    @DisplayName("markProcessed는 없는 event_id에 대해 false를 반환한다")
    fun markProcessedReturnsFalseForMissingRow() {
        assertEquals(false, repo.markProcessed("portone:never-inserted", LocalDateTime.now()))
    }
}
