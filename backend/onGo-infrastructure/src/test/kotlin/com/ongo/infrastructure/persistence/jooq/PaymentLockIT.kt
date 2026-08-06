package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.infrastructure.persistence.jooq.Tables.PAYMENTS
import org.jooq.DSLContext
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
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * `payments` 행 잠금이 **실제 PostgreSQL**에서 동시 갱신을 직렬화하는지 검증한다.
 *
 * 단위 테스트(`PaymentJooqRepositoryLockTest`)는 발행 SQL에 `FOR UPDATE`가 붙는 것까지만
 * 증명한다. 그게 실제로 동시 트랜잭션을 막는지는 DB가 있어야 알 수 있다.
 *
 * `PaymentRepository.update`는 `amount`를 갱신하지 않으므로 `description`을 카운터로 쓴다.
 * 실제 운영 경로(`findByIdForUpdate` → `update`)를 그대로 태우기 위해서다.
 *
 * 유료 파일럿 전 릴리스 게이트다. 여기서 실패하면 동시 웹훅이 크레딧을 중복 지급한다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class PaymentLockIT {

    @Autowired lateinit var repo: PaymentRepository
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

        private const val WORKERS = 6
        private const val TEST_USER_ID = 200L
    }

    @BeforeEach
    fun setUp() {
        tx = TransactionTemplate(txManager)
        dsl.deleteFrom(PAYMENTS).execute()
        dsl.execute(
            "INSERT INTO users (id, email, name, provider, provider_id, plan_type) " +
                "VALUES ($TEST_USER_ID, 'lock@test.com', 'L', 'GOOGLE', 'g200', 'FREE') " +
                "ON CONFLICT (id) DO NOTHING"
        )
    }

    private fun newPayment(): Long = repo.save(
        Payment(
            userId = TEST_USER_ID,
            type = PaymentType.CREDIT,
            amount = 9_900,
            currency = "KRW",
            status = PaymentStatus.PENDING,
            pgProvider = "portone",
            description = "0",
        )
    ).id!!

    private fun counterOf(id: Long): Int = repo.findById(id)!!.description!!.toInt()

    @Test
    @DisplayName("잠금 조회로 동시 갱신하면 갱신 손실이 없다 — 트랜잭션이 직렬화된다")
    fun lockingReadSerializesConcurrentUpdates() {
        val paymentId = newPayment()
        val pool = Executors.newFixedThreadPool(WORKERS)
        val startLine = CountDownLatch(1)

        try {
            val futures = (1..WORKERS).map {
                pool.submit(Callable {
                    startLine.await()
                    tx.execute {
                        val payment = repo.findByIdForUpdate(paymentId)!!
                        // 잠금을 쥔 채 읽기-쓰기 간격을 벌린다. 잠금이 없으면 여기서 서로를 덮어쓴다.
                        Thread.sleep(50)
                        repo.update(payment.copy(description = (payment.description!!.toInt() + 1).toString()))
                    }
                })
            }
            startLine.countDown()
            futures.forEach { it.get(60, TimeUnit.SECONDS) }

            assertEquals(
                WORKERS, counterOf(paymentId),
                "잠금이 동작하면 모든 갱신이 누적돼야 한다. 값이 작으면 갱신 손실이 일어난 것이다",
            )
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    @DisplayName("잠금 없는 조회는 갱신 손실이 일어난다 — 대조군. FOR UPDATE가 필요한 이유")
    fun plainReadLosesUpdates() {
        val paymentId = newPayment()
        val pool = Executors.newFixedThreadPool(WORKERS)
        val allRead = CountDownLatch(WORKERS)

        try {
            val futures = (1..WORKERS).map {
                pool.submit(Callable {
                    tx.execute {
                        val payment = repo.findById(paymentId)!!
                        // 잠그지 않으므로 전원이 먼저 읽을 수 있다. 결정적으로 손실을 만든다.
                        allRead.countDown()
                        allRead.await(30, TimeUnit.SECONDS)
                        repo.update(payment.copy(description = (payment.description!!.toInt() + 1).toString()))
                    }
                })
            }
            futures.forEach { it.get(60, TimeUnit.SECONDS) }

            val counter = counterOf(paymentId)
            assertTrue(
                counter < WORKERS,
                "잠금 없이 전원이 같은 값을 읽었으므로 손실이 나야 한다. 손실이 없다면 이 대조군이 무의미하다 (counter=$counter)",
            )
        } finally {
            pool.shutdownNow()
        }
    }
}
