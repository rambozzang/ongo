package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ai.PipelineCreditAllocation
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.RunStage
import com.ongo.domain.ugc.shorts.RunStageStatus
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 단계 정산이 **어떤 SQL 로 나가는지** 고정한다.
 *
 * 조건절 하나가 빠져도 코틀린은 그대로 컴파일되고 인메모리 페이크를 쓰는 단위 테스트도
 * 통과한다 — 조건이 사라졌다는 사실은 **실제 동시 정산이 들어왔을 때만** 드러나고, 그때는
 * 이미 사용자에게 두 번 환불된 뒤다. 환불은 되돌릴 수 없다.
 */
class ShortsRunStageSettlementQueryContractTest {

    private class Captured(val sql: String, val bindings: List<Any?>) {
        fun setClause(): String = sql.substringBefore(" where ")
        fun whereClause(): String = sql.substringAfter(" where ", "")
    }

    private fun capture(block: (ShortsRunStageJooqRepository) -> Unit): Captured {
        val executed = mutableListOf<Captured>()
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { context ->
            executed += Captured(context.sql().lowercase(), context.bindings().toList())
            arrayOf(MockResult(0, empty))
        }
        /*
         * `save`/`update` 는 끝에서 방금 쓴 행을 다시 읽는다. 목 커넥션은 빈 결과를 주므로
         * 그 조회가 실패하지만, **검증 대상인 쓰기 SQL 은 그 앞에서 이미 포착된다.**
         * 여기서 예외를 삼키는 것은 조회 결과가 아니라 문장 자체를 보기 때문이다.
         */
        runCatching { block(ShortsRunStageJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES))) }
        return executed.first()
    }

    /* ── 정산 표식 ────────────────────────────────────────────────────── */

    /**
     * **핵심 회귀.** 조건이 빠지면 동시에 들어온 두 정산이 모두 통과해 두 번 환불된다.
     */
    @Test
    @DisplayName("정산 표식은 RUNNING·미정산일 때만 세워진다")
    fun settleIsGuardedByStatusAndMarker() {
        val captured = capture { it.settleRefund(100L, 5, "중단됨") }
        val where = captured.whereClause()

        assertTrue(captured.sql.startsWith("update"), "정산은 단일 UPDATE 여야 원자적이다: ${captured.sql}")
        assertTrue("status" in where, "상태 조건이 없다: ${captured.sql}")
        assertTrue("refunded_credits" in where, "미정산 조건이 없다: ${captured.sql}")
        assertTrue("RUNNING" in captured.bindings, "RUNNING 만 대상으로 삼지 않는다: ${captured.bindings}")
    }

    /**
     * 표식만 세우고 단계를 열어 두면 복구기가 같은 행을 영원히 다시 집는다.
     * 완료 시각도 함께 남겨야 언제 정산됐는지 알 수 있다.
     */
    @Test
    @DisplayName("정산은 단계를 FAILED 로 닫고 완료 시각을 남긴다")
    fun settleClosesTheStage() {
        val captured = capture { it.settleRefund(100L, 5, "중단됨") }
        val set = captured.setClause()

        assertTrue("status" in set, "단계를 닫지 않는다: ${captured.sql}")
        assertTrue("completed_at" in set, "완료 시각을 남기지 않는다: ${captured.sql}")
        assertTrue("FAILED" in captured.bindings, "FAILED 로 닫지 않는다: ${captured.bindings}")
        assertTrue(5 in captured.bindings, "환불액을 표식에 남기지 않는다: ${captured.bindings}")
    }

    /* ── 미정산 조회 ──────────────────────────────────────────────────── */

    /**
     * **완료된 단계는 절대 포함되지 않아야 한다.** 그 단계는 실제로 일한 대가로 정당하게
     * 청구된 것이며, 환불하면 우리가 받은 적 없는 돈을 돌려주는 것이 된다.
     */
    @Test
    @DisplayName("미정산 조회는 RUNNING·미정산·청구액 있음을 모두 건다")
    fun unsettledQueryExcludesCompletedStages() {
        val captured = capture { it.findUnsettled(7L, 0) }
        val where = captured.whereClause()

        assertTrue("status" in where, "상태 조건이 없다: ${captured.sql}")
        assertTrue("refunded_credits" in where, "미정산 조건이 없다: ${captured.sql}")
        assertTrue("credit_cost" in where, "청구액 조건이 없다: ${captured.sql}")
        assertTrue("RUNNING" in captured.bindings, "RUNNING 만 보지 않는다: ${captured.bindings}")
    }

    /* ── 정산 재시도 후보 ─────────────────────────────────────────────── */

    /**
     * **후보 선별은 DB 가 해야 한다.** 상태만 걸고 앞에서 끊으면, 영구히 쌓이는 정산 완료
     * 실행들이 그 자리를 차지해 환불이 밀린 실행에 영영 도달하지 못한다.
     *
     * 서브질의의 컬럼은 반드시 한정돼야 한다 — 두 테이블 모두 `status` 를 갖고 있어
     * 한정이 빠지면 바깥 실행의 상태로 해석돼 조건이 조용히 뒤집힌다.
     */
    @Test
    @DisplayName("정산 재시도 후보는 미정산 단계가 있는 실패 실행만 고른다")
    fun unsettledCandidateQueryFiltersInTheDatabase() {
        val executed = mutableListOf<Captured>()
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { context ->
            executed += Captured(context.sql().lowercase(), context.bindings().toList())
            arrayOf(MockResult(0, empty))
        }
        runCatching {
            ShortsRunJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES))
                .findFailedWithUnsettledStages(50)
        }
        val sql = executed.first().sql
        val bindings = executed.first().bindings

        assertTrue("exists" in sql, "미정산 존재 여부를 질의로 걸지 않는다: $sql")
        assertTrue("ugc_shorts_run_stages" in sql, "단계 테이블을 보지 않는다: $sql")
        assertTrue(
            """"ugc_shorts_run_stages"."status"""" in sql,
            "서브질의의 status 가 한정되지 않아 바깥 상태로 해석된다: $sql",
        )
        assertTrue("refunded_credits" in sql, "미정산 조건이 없다: $sql")
        assertTrue("credit_cost" in sql, "청구액 조건이 없다: $sql")
        assertTrue("FAILED" in bindings, "실패 실행만 보지 않는다: $bindings")
        assertTrue("RUNNING" in bindings, "미정산 단계 조건이 없다: $bindings")
        assertTrue("desc" in sql, "최신순이 아니라 수기 정산 대상이 앞자리를 막는다: $sql")
    }

    /* ── 분해 보존 ────────────────────────────────────────────────────── */

    @Test
    @DisplayName("단계 저장은 차감 분해를 함께 남긴다")
    fun saveStoresTheAllocation() {
        val captured = capture {
            it.save(
                RunStage(
                    runId = 7L, stage = PipelineStage.TRANSCRIBE, status = RunStageStatus.RUNNING,
                    creditCost = 5,
                    creditAllocation = PipelineCreditAllocation(2, mapOf(11L to 3)),
                ),
            )
        }

        assertTrue("credit_allocation" in captured.sql, "분해를 저장하지 않는다: ${captured.sql}")
        assertTrue(
            captured.bindings.any { it.toString().contains("freeAmount") },
            "분해 본문이 바인딩되지 않았다: ${captured.bindings}",
        )
    }

    /**
     * **일반 갱신은 표식과 분해를 건드리면 안 된다.**
     *
     * 이 메서드는 실행 중 상태를 자주 덮어쓴다. 메모리의 기본값(null·0)이 확정된 분해나
     * 정산 표식을 지우면 환불 근거가 사라지거나 이중 환불이 열린다.
     */
    @Test
    @DisplayName("일반 갱신은 정산 표식과 분해를 건드리지 않는다")
    fun updateNeverTouchesSettlementColumns() {
        val captured = capture {
            it.update(
                RunStage(
                    id = 100L, runId = 7L, stage = PipelineStage.TRANSCRIBE,
                    status = RunStageStatus.COMPLETED, creditCost = 5,
                ),
            )
        }
        val set = captured.setClause()

        assertFalse("refunded_credits" in set, "일반 갱신이 정산 표식을 덮어쓴다: ${captured.sql}")
        assertFalse("credit_allocation" in set, "일반 갱신이 차감 분해를 덮어쓴다: ${captured.sql}")
    }
}
