package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import com.ongo.infrastructure.persistence.jooq.Tables.PAYMENTS
import com.ongo.infrastructure.persistence.jooq.Tables.WEBHOOK_EVENTS
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.sql.SQLException

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

        /** PostgreSQL SQLState — 23505 unique_violation, 23503 foreign_key_violation */
        private const val SQLSTATE_UNIQUE_VIOLATION = "23505"
        private const val SQLSTATE_FOREIGN_KEY_VIOLATION = "23503"
    }

    @BeforeEach
    fun cleanup() {
        dsl.deleteFrom(WEBHOOK_EVENTS).execute()
        dsl.deleteFrom(PAYMENTS).execute()
    }

    /** 원인 체인을 훑어 첫 SQLState를 찾는다. 벤더 예외 클래스에 직접 의존하지 않기 위함이다. */
    private fun sqlStateOf(throwable: Throwable): String? {
        var cause: Throwable? = throwable
        while (cause != null) {
            (cause as? SQLException)?.sqlState?.let { return it }
            if (cause.cause === cause) return null
            cause = cause.cause
        }
        return null
    }

    @Test
    @DisplayName("UNIQUE 위반은 스프링 DuplicateKeyException으로 번역된다")
    fun uniqueViolationTranslatesToDuplicateKeyException() {
        val event = WebhookEvent(
            eventId = "portone:contract-dup",
            eventType = "Transaction.Paid",
            payload = """{"t":1}""",
        )
        webhookRepo.save(event)

        val thrown = assertThrows<DuplicateKeyException> { webhookRepo.save(event) }

        assertTrue(
            thrown is DataIntegrityViolationException,
            "DuplicateKeyException은 DataIntegrityViolationException의 하위여야 한다",
        )
        assertEquals(
            SQLSTATE_UNIQUE_VIOLATION, sqlStateOf(thrown),
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

        assertEquals(SQLSTATE_FOREIGN_KEY_VIOLATION, sqlStateOf(thrown))
    }

    @Test
    @DisplayName("번역된 예외는 jOOQ 원본 예외가 아니다 — 자동설정이 대체되면 이 단언이 깨진다")
    fun translatedExceptionIsNotRawJooqException() {
        val event = WebhookEvent(
            eventId = "portone:contract-not-jooq",
            eventType = "Transaction.Paid",
            payload = """{"t":1}""",
        )
        webhookRepo.save(event)

        val thrown = assertThrows<DataIntegrityViolationException> { webhookRepo.save(event) }

        assertTrue(
            thrown !is org.jooq.exception.DataAccessException,
            "jOOQ 원본 예외가 올라오면 ExceptionTranslatorExecuteListener가 빠진 것이다. " +
                "그 경우 TransactionAwareDataSourceProxy도 함께 빠졌을 가능성이 크다",
        )
    }
}
