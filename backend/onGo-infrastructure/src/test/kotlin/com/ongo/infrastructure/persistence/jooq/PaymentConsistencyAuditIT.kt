package com.ongo.infrastructure.persistence.jooq

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
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.sql.DriverManager
import java.time.LocalDateTime

/**
 * `deploy/audit/payment-consistency.sql` 이 **항목마다 실제로 잡는지**, 그리고 정상 데이터에는
 * 아무것도 내지 않는지 실 PostgreSQL 에서 고정한다.
 *
 * 감사 쿼리는 운영에서 한 번 돌리고 그 결과로 복구를 정한다. 조건 하나가 틀려 조용히 0 행을
 * 내면 "깨끗하다" 는 거짓 결론이 나온다 — 그래서 불일치를 하나씩 심어 전부 나오는지 본다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class PaymentConsistencyAuditIT {

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

        /** Gradle 은 모듈 디렉터리에서 테스트를 돌린다 — backend/onGo-infrastructure. */
        private val SCRIPT = File("../../deploy/audit/payment-consistency.sql")
    }

    private var seq = 0
    private val now = LocalDateTime.now()

    @BeforeEach
    fun clean() {
        listOf(
            "ai_credit_transactions", "ai_purchased_credits", "ai_credits",
            "payments", "subscriptions", "webhook_events",
        ).forEach { dsl.execute("DELETE FROM $it") }
        dsl.execute("DELETE FROM users WHERE email LIKE '%@audit-it.test'")
    }

    // ── 픽스처 ────────────────────────────────────────────────────────────

    private fun user(plan: String = "FREE"): Long {
        seq += 1
        return dsl.fetchOne(
            "INSERT INTO users (email, name, provider, provider_id, role, plan_type) " +
                "VALUES (?, 'a', 'GOOGLE', ?, 'USER', CAST(? AS plan_type)) RETURNING id",
            "u$seq@audit-it.test", "audit-it-$seq", plan,
        )!!.get(0, Long::class.javaObjectType)
    }

    private fun payment(userId: Long, type: String, status: String, createdAt: LocalDateTime = now): Long =
        dsl.fetchOne(
            "INSERT INTO payments (user_id, type, amount, status, created_at) " +
                "VALUES (?, CAST(? AS payment_type), 9900, CAST(? AS payment_status), ?) RETURNING id",
            userId, type, status, createdAt,
        )!!.get(0, Long::class.javaObjectType)

    private fun tx(userId: Long, type: String, amount: Int, referenceId: Long? = null, createdAt: LocalDateTime = now) {
        dsl.execute(
            "INSERT INTO ai_credit_transactions (user_id, type, amount, balance_after, reference_id, created_at) " +
                "VALUES (?, CAST(? AS credit_tx_type), ?, 0, ?, ?)",
            userId, type, amount, referenceId, createdAt,
        )
    }

    private fun purchased(userId: Long, total: Int, remaining: Int) {
        dsl.execute(
            "INSERT INTO ai_purchased_credits (user_id, package_name, total_credits, remaining, price, expires_at) " +
                "VALUES (?, 'BASIC', ?, ?, 9900, ?)",
            userId, total, remaining, now.plusDays(90),
        )
    }

    private fun credits(userId: Long, balance: Int, freeRemaining: Int) {
        dsl.execute(
            "INSERT INTO ai_credits (user_id, balance, free_monthly, free_remaining, free_reset_date) " +
                "VALUES (?, ?, 30, ?, CURRENT_DATE)",
            userId, balance, freeRemaining,
        )
    }

    private fun subscription(userId: Long, plan: String, status: String, periodEnd: LocalDateTime?) {
        dsl.execute(
            "INSERT INTO subscriptions (user_id, plan_type, status, price, billing_cycle, current_period_end, next_billing_date) " +
                "VALUES (?, CAST(? AS plan_type), CAST(? AS subscription_status), 19900, CAST('MONTHLY' AS billing_cycle), ?, ?)",
            userId, plan, status, periodEnd, periodEnd,
        )
    }

    /** 스크립트의 본문 쿼리만 떼어 돌린다(SET·BEGIN·ROLLBACK 제외). */
    private fun findings(): Set<Pair<String, Long?>> {
        val text = SCRIPT.readText()
        val query = text.substring(text.indexOf("WITH findings"), text.lastIndexOf("ROLLBACK;"))
            .trim().removeSuffix(";")
        return dsl.fetch(query).map { it.get("check_name", String::class.java) to it.get("user_id", Long::class.javaObjectType) }.toSet()
    }

    // ── 테스트 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 결제·지급·구독은 아무 항목에도 걸리지 않는다")
    fun cleanDataProducesNothing() {
        val u = user(plan = "PRO")
        val credit = payment(u, "CREDIT", "COMPLETED")
        tx(u, "CHARGE", 100, credit)
        purchased(u, total = 100, remaining = 100)
        credits(u, balance = 130, freeRemaining = 30)
        payment(u, "SUBSCRIPTION", "COMPLETED")
        subscription(u, "PRO", "ACTIVE", now.plusDays(20))

        assertEquals(emptySet<Pair<String, Long?>>(), findings())
    }

    @Test
    @DisplayName("심어 둔 불일치를 항목마다 정확히 한 번씩 찾는다")
    fun detectsEverySeededInconsistency() {
        val paidNotGranted = user().also { payment(it, "CREDIT", "COMPLETED") }

        val doubleGrant = user().also { val p = payment(it, "CREDIT", "COMPLETED"); tx(it, "CHARGE", 100, p); tx(it, "CHARGE", 100, p) }

        val grantWithoutPayment = user().also { tx(it, "CHARGE", 100, payment(it, "CREDIT", "PENDING")) }

        val refundedNotRevoked = user().also { val p = payment(it, "CREDIT", "REFUNDED"); tx(it, "CHARGE", 100, p) }

        val remainingOverTotal = user().also { purchased(it, total = 100, remaining = 150) }

        val paidPlanNotApplied = user(plan = "FREE").also {
            payment(it, "SUBSCRIPTION", "COMPLETED")
            subscription(it, "PRO", "ACTIVE", now.plusDays(10))
        }

        val windowMissing = user(plan = "PRO").also { subscription(it, "PRO", "ACTIVE", null) }

        val pastPeriod = user(plan = "PRO").also { subscription(it, "PRO", "ACTIVE", now.minusDays(5)) }

        val stalePending = user().also { payment(it, "CREDIT", "PENDING", createdAt = now.minusDays(2)) }

        dsl.execute(
            "INSERT INTO webhook_events (event_id, event_type, payload, status, retry_count, max_retries) " +
                "VALUES ('evt-audit-1', 'Transaction.Paid', '{}'::jsonb, 'FAILED', 5, 5)",
        )

        val balanceDrift = user().also { credits(it, balance = 100, freeRemaining = 30) }

        val expected = setOf(
            "PAID_NOT_GRANTED" to paidNotGranted,
            "DOUBLE_GRANT" to doubleGrant,
            "GRANT_WITHOUT_PAYMENT" to grantWithoutPayment,
            "REFUNDED_NOT_REVOKED" to refundedNotRevoked,
            "REMAINING_OVER_TOTAL" to remainingOverTotal,
            "PAID_PLAN_NOT_APPLIED" to paidPlanNotApplied,
            // 같은 사용자: 구독 행은 PRO 인데 한도가 보는 users 는 FREE 다.
            "PLAN_MISMATCH" to paidPlanNotApplied,
            "PAID_WINDOW_MISSING" to windowMissing,
            "ACTIVE_PAST_PERIOD" to pastPeriod,
            "STALE_PENDING_PAYMENT" to stalePending,
            "WEBHOOK_UNPROCESSED" to null,
            "BALANCE_DRIFT" to balanceDrift,
        )
        assertEquals(expected, findings())
    }

    /**
     * psql 이 하듯 **문장 단위로** 파일 전체를 돌려도 되는지, 그리고 세션이 쓰기 불가인지 본다.
     *
     * 문장 단위여야 하는 이유: PostgreSQL 에서 `SET` 도 트랜잭션에 묶인다. JDBC 로 파일을 한
     * 문자열로 보내면 끝의 `ROLLBACK` 이 앞의 `SET` 까지 되돌려, psql 과 다른 결과를 본다.
     * 커넥션 풀을 오염시키지 않도록 별도 커넥션을 쓴다(설정이 세션에 남는다).
     */
    @Test
    @DisplayName("스크립트 전체가 읽기 전용 세션에서 오류 없이 돈다")
    fun wholeScriptRunsReadOnly() {
        payment(user(), "CREDIT", "COMPLETED")
        val statements = SCRIPT.readLines()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        assertEquals(4, statements.size, "SET · BEGIN · 본문 · ROLLBACK 외의 문장이 생겼다: $statements")

        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { conn ->
            statements.forEach { sql -> conn.createStatement().use { it.execute(sql) } }
            val readOnly = conn.createStatement().use { st ->
                st.executeQuery("SHOW default_transaction_read_only").use { rs -> rs.next(); rs.getString(1) }
            }
            assertEquals("on", readOnly)
            val writeError = runCatching {
                conn.createStatement().use { it.execute("DELETE FROM payments") }
            }.exceptionOrNull()
            assertTrue(
                writeError?.message.orEmpty().contains("read-only"),
                "감사 세션에서 쓰기가 가능했다: $writeError",
            )
        }
        assertEquals(1, dsl.fetchCount(dsl.selectFrom(Tables.PAYMENTS)))
    }
}
