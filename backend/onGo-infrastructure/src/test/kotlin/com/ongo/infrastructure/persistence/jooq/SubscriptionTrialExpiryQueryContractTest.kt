package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
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
 * 체험 만료 조회의 계약.
 *
 * ## 무엇이 문제였나
 *
 * 만료 처리(`BillingScheduler.expireTrial`)는 `plan_type`·`status` 를 FREE 로 내리지만
 * **`trial_start`·`trial_end` 는 지우지 않는다** — 체험을 이미 썼다는 사실은 재사용
 * 방지에 필요하기 때문이다.
 *
 * 그래서 조회의 두 번째 조건 `paddle_subscription_id IS NULL AND trial_start IS NOT NULL`
 * 이 **회수가 끝난 행에도 계속 참**이었다. 그 행은 매일 밤 다시 조회돼
 * "트라이얼 만료" 알림을 반복 발송했다.
 *
 * 크레딧은 안전했다 — `applyPlanEntitlement` 가 하향에서 `minOf(freeRemaining, ...)` 로
 * 올려주지 않는다. 유료 전환 고객도 안전했다 — `completeSubscription` 이 `trial_end` 를
 * null 로 지워 첫 조건에서 빠진다. 실제 피해는 **끝없이 쌓이는 중복 알림**이었다.
 */
class SubscriptionTrialExpiryQueryContractTest {

    private fun capturedSql(block: (SubscriptionJooqRepository) -> Unit): String {
        val executed = mutableListOf<String>()
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { context ->
            executed += context.sql()
            arrayOf(MockResult(0, empty))
        }
        val repository = SubscriptionJooqRepository(
            DSL.using(MockConnection(provider), SQLDialect.POSTGRES),
        )
        runCatching { block(repository) }
        return executed.single().lowercase()
    }

    /**
     * **회수가 끝난 행을 다시 집지 않는다.**
     *
     * `status` 가 아니라 `plan_type` 을 보는 이유: 두 번째 조건이 존재하는 이유가
     * "status 가 덮여 쓰여도 유료 플랜은 회수한다" 이므로, status 로 거르면 그 안전망이
     * 함께 사라진다.
     */
    @Test
    @DisplayName("이미 FREE 로 회수된 행은 조회 대상에서 빠진다")
    fun excludesAlreadyDowngradedRows() {
        val sql = capturedSql { it.findTrialExpired(LocalDateTime.of(2026, 8, 28, 2, 0)) }

        assertTrue("plan_type" in sql, "회수 완료 행을 거르는 조건이 없습니다: $sql")
        assertTrue(
            "plan_type::text <> ?" in sql || "plan_type::text != ?" in sql,
            "plan_type 을 같음이 아니라 다름으로 걸러야 합니다: $sql",
        )
    }

    /** 원래 안전망이 사라지면 status 가 덮인 체험이 유료 플랜을 무기한 유지한다. */
    @Test
    @DisplayName("status 가 덮여도 잡아내는 안전망을 유지한다")
    fun keepsStuckTrialSafetyNet() {
        val sql = capturedSql { it.findTrialExpired(LocalDateTime.of(2026, 8, 28, 2, 0)) }

        assertTrue("trial_end" in sql, sql)
        assertTrue("status::text = ?" in sql, "TRIALING 직접 조회가 사라졌습니다: $sql")
        assertTrue("paddle_subscription_id is null" in sql, "안전망 조건이 사라졌습니다: $sql")
        assertTrue("trial_start is not null" in sql, "안전망 조건이 사라졌습니다: $sql")
        // 세 조건이 OR 로 묶여 있어야 안전망이 동작한다.
        assertTrue(" or " in sql, "안전망이 AND 로 바뀌면 대상이 거의 사라집니다: $sql")
    }

    /** 바인딩 값이 FREE 여야 한다. 다른 플랜을 걸면 정상 체험이 회수되지 않는다. */
    @Test
    @DisplayName("제외 대상은 FREE 플랜이다")
    fun excludesFreePlanSpecifically() {
        val binds = mutableListOf<Any?>()
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { context ->
            binds.addAll(context.bindings().toList())
            arrayOf(MockResult(0, empty))
        }
        val repository = SubscriptionJooqRepository(
            DSL.using(MockConnection(provider), SQLDialect.POSTGRES),
        )

        runCatching { repository.findTrialExpired(LocalDateTime.of(2026, 8, 28, 2, 0)) }

        val values = binds.map { it?.toString() }
        assertTrue(PlanType.FREE.name in values, "FREE 제외 바인딩이 없습니다: $values")
        assertTrue(SubscriptionStatus.TRIALING.name in values, "TRIALING 조건이 없습니다: $values")
    }
}
