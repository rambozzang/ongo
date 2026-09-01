package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import com.ongo.infrastructure.persistence.jooq.Fields.EVENT_ID
import com.ongo.infrastructure.persistence.jooq.Tables.WEBHOOK_EVENTS
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * jOOQ `DSLContext`가 스프링 트랜잭션에 **실제로 참여하는지** 확인한다.
 *
 * 이게 성립하지 않으면 그 위에 쌓은 모든 것이 무너진다.
 * - `@Transactional handleWebhook`의 롤백이 웹훅 이력 삽입을 되돌리지 못한다
 * - `findByIdForUpdate`의 행 잠금이 SELECT 직후 풀려 중복 크레딧 지급을 막지 못한다
 *
 * 두 전제를 코드 리뷰나 단위 테스트로는 확인할 수 없다. 여기서 못 박는다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class SpringTransactionParticipationIT {

    @Autowired lateinit var repo: WebhookEventRepository
    @Autowired lateinit var dsl: DSLContext
    @Autowired lateinit var txManager: PlatformTransactionManager

    private lateinit var tx: TransactionTemplate

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
        tx = TransactionTemplate(txManager)
        dsl.deleteFrom(WEBHOOK_EVENTS).execute()
    }

    /**
     * 제약 위반을 **일부러** 일으키기 위한 평범한 INSERT.
     *
     * 저장소에는 이런 삽입이 없다. `saveIfAbsent` 는 `ON CONFLICT DO NOTHING` 이라 충돌을
     * 삼키는 것이 목적이라(멱등 게이트) 유니크 위반을 만들지 못한다. 이 테스트들이 보려는
     * 것은 "트랜잭션 안에서 DB 오류가 나면 그 뒤가 어떻게 되는가"이므로 **던지는 삽입**이
     * 반드시 필요하다.
     */
    private fun insertRaw(event: WebhookEvent) {
        dsl.insertInto(WEBHOOK_EVENTS)
            .set(EVENT_ID, event.eventId)
            .set(Fields.EVENT_TYPE, event.eventType)
            .set(DSL.field("payload", String::class.java), event.payload)
            .set(Fields.STATUS, event.status)
            .execute()
    }

    private fun event(eventId: String) = WebhookEvent(
        eventId = eventId,
        eventType = "Transaction.Paid",
        payload = """{"type":"Transaction.Paid"}""",
    )

    @Test
    @DisplayName("트랜잭션 안에서 던진 예외는 jOOQ 삽입까지 되돌린다 — 웹훅 실패 시 재전송으로 복구되는 전제")
    fun exceptionRollsBackJooqInsert() {
        val eventId = "portone:rollback-me"

        runCatching {
            tx.execute {
                repo.saveIfAbsent(event(eventId))
                throw IllegalStateException("처리 중 실패")
            }
        }

        assertEquals(
            0,
            dsl.fetchCount(WEBHOOK_EVENTS, EVENT_ID.eq(eventId)),
            "롤백되지 않으면 실패한 웹훅이 이미 수신된 것으로 남아 재전송이 전부 중복으로 걸러진다",
        )
    }

    @Test
    @DisplayName("정상 커밋된 트랜잭션의 삽입은 남는다 — 롤백 테스트의 대조군")
    fun committedInsertSurvives() {
        val eventId = "portone:commit-me"

        tx.execute { repo.saveIfAbsent(event(eventId)) }

        assertEquals(1, dsl.fetchCount(WEBHOOK_EVENTS, EVENT_ID.eq(eventId)))
    }

    @Test
    @DisplayName("트랜잭션 안에서 DB 예외를 삼키면 이후 쿼리가 전부 실패한다 — catch 후 계속 진행 패턴의 함정")
    fun swallowingDbExceptionPoisonsRestOfTransaction() {
        val existing = event("portone:poison-seed")
        insertRaw(existing)

        val secondInsertFailed = tx.execute {
            // 1) 제약 위반을 일으키고 예외를 삼킨다. 배치 루프에서 흔한 "이 건만 건너뛰고 계속" 패턴이다.
            runCatching { insertRaw(existing) }

            // 2) 같은 트랜잭션에서 다음 작업을 이어간다.
            runCatching { repo.saveIfAbsent(event("portone:poison-next")) }.isFailure
        }!!

        assertTrue(
            secondInsertFailed,
            "PostgreSQL은 문에서 오류가 나면 트랜잭션 전체를 abort 시킨다. " +
                "DB 예외를 삼키고 진행하면 이후 쿼리가 전부 실패한다",
        )
        assertEquals(
            0, dsl.fetchCount(WEBHOOK_EVENTS, EVENT_ID.eq("portone:poison-next")),
            "삼킨 뒤의 작업은 저장되지 않는다",
        )
    }

    @Test
    @DisplayName("비DB 예외를 삼키는 것은 트랜잭션에 영향이 없다 — 위 함정과 구분되는 지점")
    fun swallowingNonDbExceptionIsHarmless() {
        val saved = tx.execute {
            // AI 호출 실패, 파싱 오류 같은 비DB 예외는 트랜잭션을 오염시키지 않는다
            runCatching { throw IllegalStateException("외부 API 실패") }

            repo.saveIfAbsent(event("portone:non-db-ok"))
        }!!

        assertTrue(saved, "비DB 예외 이후에도 삽입이 정상 동작해야 한다")
        assertEquals(1, dsl.fetchCount(WEBHOOK_EVENTS, EVENT_ID.eq("portone:non-db-ok")))
    }

    @Test
    @DisplayName("항목별 REQUIRES_NEW면 한 건이 실패해도 앞선 성공은 커밋되고 다음 건도 성공한다")
    fun perItemRequiresNewIsolatesFailures() {
        // CreditScheduler 가 사용자별로 쓰는 구조다. 바깥 루프에는 트랜잭션이 없고,
        // 항목 1건이 독립 트랜잭션이며, 실패는 루프 바깥에서 잡아 다음 항목으로 넘어간다.
        val perItem = TransactionTemplate(txManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
        val duplicate = event("portone:item-2")
        insertRaw(duplicate) // 2번 항목이 반드시 실패하도록 미리 심는다

        val processed = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (n in 1..3) {
            val id = "portone:item-$n"
            try {
                perItem.executeWithoutResult {
                    // 항목마다 두 번 쓴다. 실패 시 앞의 쓰기까지 롤백되는지 보기 위함이다.
                    repo.saveIfAbsent(event("$id-marker"))
                    if (n == 2) insertRaw(duplicate) // UNIQUE 위반
                }
                processed += id
            } catch (e: Exception) {
                failed += id
            }
        }

        assertEquals(listOf("portone:item-1", "portone:item-3"), processed, "1번과 3번은 성공해야 한다")
        assertEquals(listOf("portone:item-2"), failed, "2번만 실패해야 한다")

        // 앞선 성공은 커밋됐다
        assertEquals(1, dsl.fetchCount(WEBHOOK_EVENTS, EVENT_ID.eq("portone:item-1-marker")))
        // 실패 건은 통째로 롤백됐다 — 실패 전에 쓴 marker 도 남지 않는다
        assertEquals(
            0, dsl.fetchCount(WEBHOOK_EVENTS, EVENT_ID.eq("portone:item-2-marker")),
            "실패한 항목의 부분 쓰기가 남으면 안 된다",
        )
        // 실패 이후에도 다음 항목이 정상 처리됐다 — 트랜잭션 오염이 전파되지 않는다
        assertEquals(1, dsl.fetchCount(WEBHOOK_EVENTS, EVENT_ID.eq("portone:item-3-marker")))
    }

    @Test
    @DisplayName("같은 트랜잭션 안의 두 jOOQ 호출은 같은 커넥션을 쓴다 — 행 잠금이 유지되는 전제")
    fun sameTransactionSharesConnection() {
        val pids = tx.execute {
            val first = dsl.fetchOne("select pg_backend_pid()")!!.get(0) as Int
            val second = dsl.fetchOne("select pg_backend_pid()")!!.get(0) as Int
            first to second
        }!!

        assertEquals(
            pids.first, pids.second,
            "커넥션이 다르면 SELECT ... FOR UPDATE 의 잠금이 다음 쿼리 전에 풀린다",
        )
    }
}
