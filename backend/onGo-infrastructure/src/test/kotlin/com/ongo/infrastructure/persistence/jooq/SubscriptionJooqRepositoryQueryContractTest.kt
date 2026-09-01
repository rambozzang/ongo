package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.PlanType
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.conf.StatementType
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SubscriptionJooqRepositoryQueryContractTest {

    @Test
    fun `billing query excludes both pending plan and pending billing cycle`() {
        val executed = mutableListOf<String>()
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { context ->
            executed += context.sql()
            arrayOf(MockResult(0, empty))
        }
        val repository = SubscriptionJooqRepository(
            DSL.using(MockConnection(provider), SQLDialect.POSTGRES),
        )

        repository.findDueForBilling(LocalDateTime.of(2026, 8, 27, 12, 0))

        val sql = executed.single().lowercase()
        assertTrue("pending_plan_type is null" in sql, "예약 플랜이 있는 구독을 갱신 대상으로 조회합니다: $sql")
        assertTrue("pending_billing_cycle is null" in sql, "예약 주기만 남은 비정상 행을 갱신 대상으로 조회합니다: $sql")
    }

    @Test
    fun `pending lookup includes rows with only a pending billing cycle for anomaly detection`() {
        val executed = mutableListOf<String>()
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { context ->
            executed += context.sql()
            arrayOf(MockResult(0, empty))
        }
        val repository = SubscriptionJooqRepository(
            DSL.using(MockConnection(provider), SQLDialect.POSTGRES),
        )

        repository.findWithPendingPlanType()

        val sql = executed.single().lowercase()
        assertTrue("pending_plan_type is not null" in sql, "예약 플랜 조회 조건이 사라졌습니다: $sql")
        assertTrue("pending_billing_cycle is not null" in sql, "예약 주기만 남은 이상 행을 감지하지 못합니다: $sql")
    }

    /**
     * 취소 구독의 Free 전환 조회는 **기간이 비어 있는 행도 잡아야 한다.**
     *
     * SQL 에서 NULL 과의 `<` 비교는 참이 아니라 UNKNOWN 이다. 조건을
     * `current_period_end < now` 로만 두면 기간이 비어 있는 취소 구독이 영원히 선택되지
     * 않고, 다른 조회에도 걸리지 않아 **유료 planType 이 영구히 남는다.**
     */
    @Test
    fun `cancelled expiry query also selects rows with a null billing period`() {
        val executed = mutableListOf<String>()
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { context ->
            executed += context.sql()
            arrayOf(MockResult(0, empty))
        }
        /*
         * 값을 인라인으로 렌더링한다. 기본값(바인드 파라미터)이면 상태값이 `?` 로 나와
         * "CANCELLED 를 본다" 는 조건을 실제로 검증할 수 없다.
         */
        val repository = SubscriptionJooqRepository(
            DSL.using(
                MockConnection(provider),
                SQLDialect.POSTGRES,
                Settings().withStatementType(StatementType.STATIC_STATEMENT),
            ),
        )

        repository.findCancelledExpired(LocalDateTime.of(2026, 8, 27, 12, 0))

        val sql = executed.single().lowercase()
        assertTrue(
            "current_period_end is null" in sql,
            "기간이 비어 있는 취소 구독이 Free 전환 대상에서 빠집니다: $sql",
        )
        // 기존 조건도 그대로 있어야 기간이 남은 구독을 앞당겨 끊지 않는다.
        assertTrue(
            "current_period_end <" in sql,
            "기간 만료 조건이 사라져 아직 기간이 남은 구독까지 전환됩니다: $sql",
        )
        assertTrue("status" in sql && "cancelled" in sql, "취소 상태 조건이 사라졌습니다: $sql")
    }

    /** 실행된 SQL 한 건을 소문자로 돌려준다. 값은 인라인으로 렌더링해 조건을 실제로 본다. */
    private fun capturedSql(block: (SubscriptionJooqRepository) -> Unit): String {
        val executed = mutableListOf<String>()
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { context ->
            executed += context.sql()
            arrayOf(MockResult(0, empty))
        }
        block(
            SubscriptionJooqRepository(
                DSL.using(
                    MockConnection(provider),
                    SQLDialect.POSTGRES,
                    Settings().withStatementType(StatementType.STATIC_STATEMENT),
                ),
            ),
        )
        return executed.single().lowercase()
    }

    /**
     * 유료 플랜 조회는 **정상 청구창이 있는 행만** 돌려줘야 한다.
     *
     * 이 조회 결과가 곧 유료 스케줄러 작업(`CommentSyncScheduler`, `WeeklyDigestScheduler`)의
     * 대상이다. 기간이 비어 있는 유료 구독은 청구되지도 만료되지도 않는데(NULL 비교가
     * UNKNOWN), 여기에 걸리면 **돈은 한 번도 걷히지 않으면서 유료 작업만 매 주기 나간다.**
     * 운영에서 실제로 그런 구독이 발견됐다.
     */
    @Test
    fun `paid plan lookup requires a complete billing window`() {
        val sql = capturedSql { it.findByPlanType(PlanType.BUSINESS) }

        assertTrue(
            "current_period_start is not null" in sql,
            "기간 시작이 비어 있는 유료 구독이 유료 작업 대상에 남습니다: $sql",
        )
        assertTrue(
            "current_period_end is not null" in sql,
            "기간 종료가 비어 있는 유료 구독이 유료 작업 대상에 남습니다: $sql",
        )
        assertTrue(
            "next_billing_date is not null" in sql,
            "다음 청구일이 비어 있는 유료 구독이 유료 작업 대상에 남습니다: $sql",
        )
        // 기존 의미도 그대로여야 한다 — 살아 있는 구독만 본다.
        assertTrue("business" in sql, "요금제 조건이 사라졌습니다: $sql")
        assertTrue("active" in sql && "free" in sql, "살아 있는 상태 조건이 사라졌습니다: $sql")
    }

    /**
     * **FREE 는 청구창을 요구하지 않는다.**
     *
     * 무료 구독에는 청구 기간이 없다. `initializeNewUser` 가 만드는 정상 FREE 행부터
     * 기간이 NULL 이므로, 같은 조건을 걸면 무료 사용자가 통째로 조회에서 사라진다.
     */
    @Test
    fun `free plan lookup keeps its previous meaning`() {
        val sql = capturedSql { it.findByPlanType(PlanType.FREE) }

        assertTrue(
            "is not null" !in sql,
            "무료 구독에 청구창을 요구해 정상 FREE 사용자가 조회에서 빠집니다: $sql",
        )
        assertTrue("free" in sql, "요금제 조건이 사라졌습니다: $sql")
    }
}
