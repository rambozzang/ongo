package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.subscription.SubscriptionRepository
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

/**
 * 자동 갱신 없이 기간이 끝난 구독을 **실제 PostgreSQL 에서** 정확히 고르는지 고정한다.
 *
 * 이 조회가 넓으면 돈을 낸 기간이 남은 고객을 Free 로 내리고, 좁으면 한 번 결제로 유료 플랜을
 * 영구히 쓰는 구멍이 그대로 남는다. 경계(`COALESCE(current_period_end, next_billing_date)`)와
 * NULL 처리는 SQL 의미에 달려 있어 목으로는 확인할 수 없다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class SubscriptionExpiryWithoutRenewalIT {

    @Autowired lateinit var subscriptionRepository: SubscriptionRepository
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

        private const val EMAIL_DOMAIN = "@expiry-it.test"
    }

    private val now: LocalDateTime = LocalDateTime.of(2026, 9, 23, 2, 0)
    private var seq = 0

    @BeforeEach
    fun clean() {
        dsl.execute("DELETE FROM subscriptions WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", "%$EMAIL_DOMAIN")
        dsl.execute("DELETE FROM users WHERE email LIKE ?", "%$EMAIL_DOMAIN")
    }

    /** @return subscriptions.id */
    private fun subscription(
        plan: String = "PRO",
        status: String = "ACTIVE",
        periodEnd: LocalDateTime? = null,
        nextBilling: LocalDateTime? = null,
        paddleId: String? = null,
        pendingPlan: String? = null,
    ): Long {
        seq += 1
        val userId = dsl.fetchOne(
            "INSERT INTO users (email, name, provider, provider_id, role, plan_type) " +
                "VALUES (?, 'u', 'GOOGLE', ?, 'USER', CAST(? AS plan_type)) RETURNING id",
            "u$seq$EMAIL_DOMAIN", "expiry-it-$seq", plan,
        )!!.get(0, Long::class.javaObjectType)
        return dsl.fetchOne(
            """
            INSERT INTO subscriptions (user_id, plan_type, status, price, billing_cycle,
                                       current_period_end, next_billing_date, paddle_subscription_id, pending_plan_type)
            VALUES (?, CAST(? AS plan_type), CAST(? AS subscription_status), 19900, CAST('MONTHLY' AS billing_cycle), ?, ?, ?, ?)
            RETURNING id
            """.trimIndent(),
            userId, plan, status, periodEnd, nextBilling, paddleId, pendingPlan,
        )!!.get(0, Long::class.javaObjectType)
    }

    private fun expiredIds() = subscriptionRepository.findActiveExpiredWithoutRenewal(now).map { it.id }.toSet()

    @Test
    @DisplayName("기간이 끝난 유료 ACTIVE 만 고르고, 남은 기간·다른 상태·FREE·Paddle·하향 예약은 뺀다")
    fun selectsOnlyExpiredPaidActive() {
        val expired = subscription(periodEnd = now.minusDays(1))
        val endsExactlyNow = subscription(periodEnd = now)
        val expiredByNextBilling = subscription(nextBilling = now.minusHours(1))
        subscription(periodEnd = now.plusDays(1))                                   // 남은 기간 보호
        subscription(status = "CANCELLED", periodEnd = now.minusDays(1))           // 취소 만료 단계 몫
        subscription(status = "PAST_DUE", periodEnd = now.minusDays(1))            // 유예 단계 몫
        subscription(plan = "FREE", status = "ACTIVE", periodEnd = now.minusDays(1))
        subscription(periodEnd = now.minusDays(1), paddleId = "sub_legacy")         // Paddle 이 관리
        subscription(periodEnd = now.minusDays(1), pendingPlan = "STARTER")         // 하향 적용 단계 몫

        assertEquals(setOf(expired, endsExactlyNow, expiredByNextBilling), expiredIds())
    }

    /**
     * 기간 값이 전혀 없는 유료 ACTIVE 는 **자동 강등하지 않는다.** 결제로 만들어진 기간이 아니라
     * 근거가 없다 — 감사 SQL 의 PAID_WINDOW_MISSING 으로 사람이 본다.
     */
    @Test
    @DisplayName("기간을 모르는 유료 구독은 자동 강등 대상이 아니다")
    fun unknownWindowIsNotAutoExpired() {
        subscription(periodEnd = null, nextBilling = null)

        assertEquals(emptySet<Long>(), expiredIds())
    }

    @Test
    @DisplayName("만료 예고 창은 [from, to) 반열린 구간이다")
    fun endingBetweenIsHalfOpen() {
        val from = now.plusDays(3)
        val atFrom = subscription(periodEnd = from)
        val inside = subscription(periodEnd = from.plusHours(12))
        subscription(periodEnd = from.plusDays(1))            // to 는 제외 — 다음 날 창의 몫
        subscription(periodEnd = from.minusSeconds(1))
        subscription(status = "CANCELLED", periodEnd = from.plusHours(1))

        val ids = subscriptionRepository.findActiveEndingBetween(from, from.plusDays(1)).map { it.id }.toSet()
        assertEquals(setOf(atFrom, inside), ids)
    }
}
