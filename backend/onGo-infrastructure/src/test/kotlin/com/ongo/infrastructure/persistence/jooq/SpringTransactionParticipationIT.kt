package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import com.ongo.infrastructure.persistence.jooq.Fields.EVENT_ID
import com.ongo.infrastructure.persistence.jooq.Tables.WEBHOOK_EVENTS
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
import org.springframework.transaction.PlatformTransactionManager
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
