package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import com.ongo.infrastructure.persistence.jooq.Tables.PAYMENTS
import com.ongo.infrastructure.persistence.jooq.Tables.WEBHOOK_EVENTS
import com.ongo.infrastructure.testsupport.SqlStates
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * 제약 위반 시 올라오는 **예외 타입 계약**을 고정한다.
 *
 * `JooqConfig`가 스프링 부트 `JooqAutoConfiguration`에 위임하므로
 * `ExceptionTranslatorExecuteListener`가 붙어 jOOQ 예외가 스프링 `DataAccessException`
 * 계층으로 번역된다. 이 계약이 깨지면 자동설정이 다시 대체됐다는 뜻이고,
 * 그때는 트랜잭션 참여도 함께 깨졌을 가능성이 크다([SpringTransactionParticipationIT] 참조).
 *
 * 벤더 예외 클래스(`org.postgresql.util.PSQLException`)를 직접 단언하지 않는다.
 * 스프링 계층 + SQLState로 계약을 두어 Boot/JDBC 드라이버 패치에 덜 흔들리게 한다.
 *
 * 참고: 프로덕션 코드에서 예외 타입에 의존하는 곳은 없다(2026-08-06 전수 조사).
 * 이 계약의 소비자는 테스트뿐이다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class JooqExceptionContractIT {

    @Autowired lateinit var webhookRepo: WebhookEventRepository
    @Autowired lateinit var paymentRepo: PaymentRepository
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
        dsl.deleteFrom(WEBHOOK_EVENTS).execute()
        dsl.deleteFrom(PAYMENTS).execute()
    }

    /**
     * 제약 위반을 일으키기 위한 **평범한 INSERT**.
     *
     * 저장소에는 이런 삽입이 없다. `saveIfAbsent` 는 `ON CONFLICT DO NOTHING` 이라 충돌을
     * 삼키는 것이 목적이고(멱등 게이트), 그래서 유니크 위반을 만들지 못한다. 여기서 보려는
     * 것은 저장소 메서드가 아니라 **jOOQ 예외를 스프링 예외로 번역하는 리스너가 붙어
     * 있는가**이며, 그 리스너는 `DSLContext` 에 달려 있으므로 직접 삽입해도 동일하게
     * 검증된다.
     */
    private fun insertRaw(event: WebhookEvent) {
        dsl.insertInto(WEBHOOK_EVENTS)
            .set(Fields.EVENT_ID, event.eventId)
            .set(Fields.EVENT_TYPE, event.eventType)
            .set(DSL.field("payload", String::class.java), event.payload)
            .set(Fields.STATUS, event.status)
            .execute()
    }


    @Test
    @DisplayName("UNIQUE 위반은 스프링 DuplicateKeyException으로 번역된다")
    fun uniqueViolationTranslatesToDuplicateKeyException() {
        val event = WebhookEvent(
            eventId = "portone:contract-dup",
            eventType = "Transaction.Paid",
            payload = """{"t":1}""",
        )
        insertRaw(event)

        // DuplicateKeyException 은 DataIntegrityViolationException 의 하위다.
        // 상위 타입 여부는 컴파일 타임 사실이라 단언하지 않는다.
        val thrown = assertThrows<DuplicateKeyException> { insertRaw(event) }

        assertEquals(
            SqlStates.UNIQUE_VIOLATION, SqlStates.of(thrown),
            "SQLState까지 계약으로 둔다. 벤더 예외 클래스에는 의존하지 않는다",
        )
    }

    @Test
    @DisplayName("FK 위반은 스프링 DataIntegrityViolationException으로 번역된다")
    fun foreignKeyViolationTranslatesToDataIntegrityViolationException() {
        val thrown = assertThrows<DataIntegrityViolationException> {
            paymentRepo.save(
                Payment(
                    userId = 99_999_999L, // users에 없는 id
                    type = PaymentType.CREDIT,
                    amount = 9_900,
                    currency = "KRW",
                    status = PaymentStatus.PENDING,
                    pgProvider = "portone",
                    description = "CREDIT|BASIC",
                )
            )
        }

        assertEquals(SqlStates.FOREIGN_KEY_VIOLATION, SqlStates.of(thrown))
    }

    @Test
    @DisplayName("번역 리스너가 빠지면 이 테스트가 깨진다 — 자동설정 대체 감지")
    fun translatorIsWired() {
        val event = WebhookEvent(
            eventId = "portone:contract-not-jooq",
            eventType = "Transaction.Paid",
            payload = """{"t":1}""",
        )
        insertRaw(event)

        // 번역 리스너가 빠지면 jOOQ 원본 예외가 올라와 이 assertThrows 자체가 실패한다.
        // 그게 곧 감지 수단이다. 별도의 `!is jOOQ 예외` 단언은 컴파일 타임에 참이라 두지 않는다.
        // 이 테스트가 깨지면 TransactionAwareDataSourceProxy도 함께 빠졌을 가능성이 크므로
        // SpringTransactionParticipationIT 를 반드시 함께 확인한다.
        val thrown = assertThrows<DataIntegrityViolationException> { insertRaw(event) }

        assertEquals(SqlStates.UNIQUE_VIOLATION, SqlStates.of(thrown))
    }
}
