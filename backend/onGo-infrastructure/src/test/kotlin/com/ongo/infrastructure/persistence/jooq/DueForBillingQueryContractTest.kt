package com.ongo.infrastructure.persistence.jooq

import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * 자동 갱신 대상 조회가 **어떤 SQL 로 나가는지** 고정한다.
 *
 * 이 목록은 고객 카드에 실제로 청구할 대상이다. 조건 하나가 빠지면 돈이 잘못 움직인다.
 *
 * ## 특히 Paddle
 *
 * [com.ongo.application.subscription.SubscriptionRenewalService] 는 **PortOne 빌링키로**
 * 청구한다. 그런데 Paddle 로 결제한 레거시 구독도 `status=ACTIVE` 이고 `next_billing_date`
 * 가 채워져 있어(Paddle 의 `next_billed_at` 을 그대로 저장한다) 조건에 그대로 걸린다.
 *
 *  - 빌링키가 없으면 → PAST_DUE → 7일 뒤 Free. **Paddle 에서는 정상 결제 중인데
 *    우리 쪽에서만 권한을 뺏는다.**
 *  - 빌링키가 있으면 → Paddle 과 PortOne 이 같은 주기를 각각 청구한다. **이중 청구.**
 *
 * 이 결함은 `subscription.renewal.enabled` 가 켜진 뒤에야 드러나므로, 조건이 사라져도
 * 평소 테스트로는 알 수 없다. SQL 을 직접 본다.
 *
 * **DB 를 띄우지 않는다.** jOOQ 목 커넥션으로 생성된 SQL 만 확인한다.
 */
class DueForBillingQueryContractTest {

    private fun capturedSql(): String {
        val executed = mutableListOf<String>()
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { context ->
            executed += context.sql().lowercase()
            arrayOf(MockResult(0, empty))
        }
        val repository = SubscriptionJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES))
        runCatching { repository.findDueForBilling(LocalDateTime.now()) }
        return executed.first()
    }

    /** 조회 자체가 성립하는지. 아래 단언들이 공허하게 통과하는 것을 막는다. */
    @Test
    @DisplayName("subscriptions 를 조회한다")
    fun selectsFromSubscriptions() {
        val sql = capturedSql()

        assertTrue(sql.startsWith("select"), "조회가 아니다: $sql")
        assertTrue("subscriptions" in sql, "구독 테이블을 보지 않는다: $sql")
    }

    /**
     * **핵심 회귀.** 이 조건이 없으면 레거시 Paddle 구독이 PortOne 청구 대상이 된다.
     */
    @Test
    @DisplayName("Paddle 구독을 제외한다")
    fun excludesPaddleSubscriptions() {
        val sql = capturedSql()

        assertTrue(
            "paddle_subscription_id is null" in sql,
            "Paddle 구독을 제외하지 않는다 — 이중 청구 또는 정상 결제 중인 고객의 Free 강등: $sql",
        )
    }

    /* ── 기존 조건도 함께 지킨다. 하나를 고치다 다른 하나를 잃지 않게. ── */

    /** ACTIVE 가 아닌 구독을 청구하면 해지·연체 고객에게 돈을 걷는다. */
    @Test
    @DisplayName("ACTIVE 구독만 대상으로 한다")
    fun onlyActiveSubscriptions() {
        val sql = capturedSql()

        assertTrue("status" in sql, "상태 조건이 없다: $sql")
    }

    /** 주기가 아직 오지 않은 구독을 청구하면 선결제가 된다. */
    @Test
    @DisplayName("청구일이 지난 구독만 대상으로 한다")
    fun onlyDueSubscriptions() {
        val sql = capturedSql()

        assertTrue("next_billing_date" in sql, "청구일 조건이 없다: $sql")
    }

    /**
     * 하향 예약이 남은 구독은 **옛 플랜 가격으로** 청구된다. 기간 경계에서 하향을 먼저
     * 적용한 뒤에만 갱신 대상이어야 한다.
     */
    @Test
    @DisplayName("하향 예약이 남은 구독을 제외한다")
    fun excludesPendingDowngrades() {
        val sql = capturedSql()

        assertTrue("pending_plan_type is null" in sql, "예약 플랜 조건이 없다: $sql")
        assertTrue("pending_billing_cycle is null" in sql, "예약 주기 조건이 없다: $sql")
    }
}
