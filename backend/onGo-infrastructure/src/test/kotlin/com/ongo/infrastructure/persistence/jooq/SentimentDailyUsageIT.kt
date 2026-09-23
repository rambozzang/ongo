package com.ongo.infrastructure.persistence.jooq

import com.ongo.application.ai.SentimentUsageRepository
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * 자동 감정 분석의 하루 배치 상한이 **동시 요청에서도** 넘지 않는지 실 PostgreSQL 에서 고정한다.
 *
 * 상한이 곧 요금제별 원가 상한이다(AiUnitEconomics.sentimentMonthlyCapKrw). 동시 동기화 두 개가 같은 카운트를 읽고
 * 둘 다 통과하면 원가 상한이 깨진다. 조건부 upsert 한 문장이 그것을 막는지 본다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class SentimentDailyUsageIT {

    @Autowired lateinit var repository: SentimentUsageRepository
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

    private var userId = 0L
    private val today = LocalDate.of(2026, 9, 24)

    @BeforeEach
    fun setUp() {
        dsl.execute("DELETE FROM ai_sentiment_daily_usage")
        dsl.execute("DELETE FROM users WHERE email = 'sentiment-it@test.io'")
        userId = dsl.fetchOne(
            "INSERT INTO users (email, name, provider, provider_id, role) VALUES ('sentiment-it@test.io','s','GOOGLE','sentiment-it','USER') RETURNING id",
        )!!.get(0, Long::class.javaObjectType)
    }

    @Test
    @DisplayName("동시에 20개가 들어와도 하루 상한 8개만 통과한다")
    fun concurrentBatchesNeverExceedDailyLimit() {
        val pool = Executors.newFixedThreadPool(20)
        val start = CountDownLatch(1)
        val results = (1..20).map {
            pool.submit(Callable { start.await(); repository.tryConsumeBatch(userId, today, 8) })
        }
        start.countDown()
        val admitted = results.count { it.get() }
        pool.shutdown()

        assertEquals(8, admitted)
        assertEquals(8, dsl.fetchOne("SELECT batch_count FROM ai_sentiment_daily_usage WHERE user_id = ?", userId)!!.get(0, Int::class.java))
    }

    @Test
    @DisplayName("날짜가 바뀌면 다시 센다")
    fun resetsNextDay() {
        repeat(8) { assertTrue(repository.tryConsumeBatch(userId, today, 8)) }
        assertFalse(repository.tryConsumeBatch(userId, today, 8))

        assertTrue(repository.tryConsumeBatch(userId, today.plusDays(1), 8))
    }
}
