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
import org.jooq.exception.IntegrityConstraintViolationException
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * 제약 위반 시 **어떤 예외 타입이 올라오는지** 현재 계약을 고정한다.
 *
 * `JooqConfig`가 커스텀 `DefaultConfiguration` 빈이라, 스프링 부트의
 * `JooqAutoConfiguration`이 함께 구성하는 `ExceptionTranslatorExecuteListener`가 빠져 있다.
 * 그래서 지금은 jOOQ 원본 예외([IntegrityConstraintViolationException])가 그대로 올라온다.
 *
 * 자동설정으로 전환하면 이 예외가 스프링 `DataAccessException` 계층으로 **번역된다.**
 * 그 전환이 무엇을 바꾸는지 눈에 보이게 하려고 이 테스트를 먼저 남긴다.
 *
 * 조사 결과(2026-08-06):
 * - **프로덕션 코드에서 jOOQ 예외 타입에 의존하는 곳은 0건이다.**
 * - 테스트에서 직접 단언하는 곳은 9군데(6파일)다:
 *   `ContentSourceJooqRepositoryIT:78`, `UgcSubmissionJooqRepositoryIT:83`,
 *   `UgcRewardJooqRepositoryIT:87,117`, `DriveImportJobJooqRepositoryIT:122`,
 *   `UgcCampaignJooqRepositoryIT:129,138,149`, `UgcCampaignPostJooqRepositoryIT:87`
 *
 * 전환 시 이 파일의 단언을 뒤집고 위 9곳을 함께 조정하면 된다.
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

    @Test
    @DisplayName("UNIQUE 위반은 jOOQ 원본 예외로 올라온다 — 자동설정 전환 시 이 단언이 바뀐다")
    fun uniqueViolationSurfacesAsJooqException() {
        val event = WebhookEvent(
            eventId = "portone:contract-dup",
            eventType = "Transaction.Paid",
            payload = """{"t":1}""",
        )
        webhookRepo.save(event)

        val thrown = assertThrows<IntegrityConstraintViolationException> {
            webhookRepo.save(event)
        }

        // 스프링 예외 계층으로 번역되지 **않는다**는 것이 현재 계약이다
        assertTrue(
            thrown !is DataAccessException,
            "스프링 DataAccessException으로 번역되면 자동설정이 켜진 것이다. 9곳의 단언을 함께 갱신해야 한다",
        )
    }

    @Test
    @DisplayName("FK 위반도 jOOQ 원본 예외로 올라온다")
    fun foreignKeyViolationSurfacesAsJooqException() {
        assertThrows<IntegrityConstraintViolationException> {
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
    }
}
