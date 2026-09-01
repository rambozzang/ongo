package com.ongo.infrastructure.persistence.jooq

import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 실행 확보가 **어떤 SQL 로 나가는지** 고정한다.
 *
 * `claimRunning` 의 존재 이유는 조건절 하나뿐이다. 그 조건이 빠지거나 넓어져도 코틀린은
 * 그대로 컴파일되고 인메모리 페이크를 쓰는 단위 테스트도 통과할 수 있다 — 조건이 바뀌었다는
 * 사실은 **실제 중복 이벤트가 들어왔을 때만** 드러나고, 그때는 이미 사용자가 두 번 청구된
 * 뒤다. 전사는 원본 길이에 비례해 매기므로 긴 영상일수록 손해가 크다.
 *
 * ## 왜 하필 `PENDING` 인가
 *
 * 실행 이벤트를 내는 다섯 곳이 **모두 발행 직전에 PENDING 으로 전환한다**(생성·단계 재실행·
 * 후킹 확정·예약 확정·자동 예약 워커). 그러므로 PENDING 이 아닌 상태로 들어온 요청은 중복이다.
 *
 * 조건을 "RUNNING·CANCELLED 만 제외" 로 넓히면 겹쳐 도착한 중복만 막힌다. 첫 실행이
 * 게이트(`AWAITING_HOOK_SELECTION`)나 완료(`COMPLETED`)에 도달한 **뒤에** 도착한 중복은
 * 그대로 통과해 파이프라인이 처음부터 다시 돈다.
 */
class ShortsRunClaimQueryContractTest {

    private class Captured(val sql: String, val bindings: List<Any?>) {
        /** `SET` 절만. `WHERE` 의 조건과 갱신 대상을 섞어 보지 않기 위해 나눈다. */
        fun setClause(): String = sql.substringBefore(" where ")
        fun whereClause(): String = sql.substringAfter(" where ", "")
    }

    private fun capture(block: (ShortsRunJooqRepository) -> Unit): Captured {
        val executed = mutableListOf<Captured>()
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { context ->
            executed += Captured(context.sql().lowercase(), context.bindings().toList())
            arrayOf(MockResult(0, empty))
        }
        block(ShortsRunJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES)))
        return executed.single()
    }

    @Test
    @DisplayName("확보는 단일 UPDATE 로 원자적으로 수행한다")
    fun claimIsASingleAtomicUpdate() {
        val captured = capture { it.claimRunning(1L) }

        assertTrue(captured.sql.startsWith("update"), "확보는 단일 UPDATE 여야 원자적이다: ${captured.sql}")
        assertTrue("ugc_shorts_pipeline_runs" in captured.sql, captured.sql)
    }

    /** **핵심 회귀.** 조건이 넓어지면 게이트·완료 뒤에 도착한 중복이 통과한다. */
    @Test
    @DisplayName("확보 조건은 PENDING 동등 비교 하나다")
    fun claimMatchesOnlyPendingRuns() {
        val captured = capture { it.claimRunning(1L) }

        assertTrue("status =" in captured.sql, "상태 동등 조건이 없다: ${captured.sql}")
        // 부정 조건으로 넓히면 COMPLETED·AWAITING_* 가 통과한다.
        assertFalse("status <>" in captured.sql, "확보 조건이 부정 비교로 넓어졌다: ${captured.sql}")
        assertFalse("status !=" in captured.sql, "확보 조건이 부정 비교로 넓어졌다: ${captured.sql}")

        assertTrue("PENDING" in captured.bindings, "PENDING 이 조건으로 묶이지 않았다: ${captured.bindings}")
        assertTrue("RUNNING" in captured.bindings, "RUNNING 으로 전환하지 않는다: ${captured.bindings}")
        // 상태 조건은 하나여야 한다. 늘어나면 그만큼 확보 범위가 넓어졌다는 뜻이다.
        assertEquals(1, captured.whereClause().split("status").size - 1, "상태 조건이 하나가 아니다: ${captured.sql}")
    }

    /**
     * **확보는 진척으로 기록되어야 한다.**
     *
     * `version`·`updated_at` 을 함께 옮기지 않으면, 확보 직후 오케스트레이터가 생존 등록을
     * 마치기 전 창에서 고착 복구기가 **확보 이전에 읽은 version 그대로** CAS 에 성공한다.
     * 방금 시작한 실행이 FAILED 로 바뀌고, 그 사이 사용자가 재실행을 누르면 같은 작업이 두 번
     * 청구된다. 이 갱신이 있으면 낡은 관측이 조건 자체로 빗나가므로 안전이 레지스트리 등록
     * 타이밍에 의존하지 않는다.
     */
    @Test
    @DisplayName("확보는 version 을 올리고 updated_at 을 갱신한다")
    fun claimRecordsProgress() {
        val captured = capture { it.claimRunning(1L) }
        val setClause = captured.setClause()

        assertTrue("version" in setClause, "확보가 version 을 올리지 않는다: ${captured.sql}")
        assertTrue("updated_at" in setClause, "확보가 updated_at 을 갱신하지 않는다: ${captured.sql}")
        // 읽어서 더하면 그 사이의 갱신을 덮어쓴다. SQL 안에서 계산해야 한다.
        assertTrue(
            Regex("""version"?\s*=\s*\(?\s*"?version""").containsMatchIn(setClause),
            "version 을 SQL 안에서 증가시키지 않는다: ${captured.sql}",
        )
    }

    /* ── 고착 복구 ─────────────────────────────────────────────────── */

    /**
     * **복구는 `FAILED` 로만 되돌린다.**
     *
     * `FAILED` 는 확보 조건(`PENDING`)이 아니므로 이 갱신은 어떤 작업도 다시 실행시키지 않는다.
     * `PENDING` 으로 바꾸면 다음 이벤트가 곧바로 확보해 자동 재실행되고, 살아 있는 작업과
     * 겹치면 같은 단계가 두 번 청구된다.
     */
    @Test
    @DisplayName("고착 복구는 RUNNING 을 FAILED 로만 되돌린다")
    fun staleRecoveryOnlyMarksFailed() {
        val captured = capture { it.failStale(1L, 7L, "중단됨") }

        assertTrue(captured.sql.startsWith("update"), captured.sql)
        assertTrue("FAILED" in captured.bindings, "FAILED 로 되돌리지 않는다: ${captured.bindings}")
        assertFalse("PENDING" in captured.bindings, "복구가 자동 재실행 가능 상태로 되돌린다: ${captured.bindings}")
    }

    /**
     * 관측한 `version` 과 `RUNNING` 을 **함께** 조건에 둔다. 읽은 뒤 살아 있는 작업이 단계를
     * 하나라도 넘겼다면 0행이 되어 복구가 취소된다. 조건이 빠지면 진행 중인 작업을 덮어쓴다.
     */
    @Test
    @DisplayName("복구는 관측한 version 과 RUNNING 을 함께 조건으로 건다")
    fun staleRecoveryIsGuardedByVersionAndStatus() {
        val captured = capture { it.failStale(1L, 7L, "중단됨") }

        assertTrue("version =" in captured.sql, "version 조건이 없다: ${captured.sql}")
        assertTrue("status =" in captured.sql, "상태 조건이 없다: ${captured.sql}")
        assertTrue("RUNNING" in captured.bindings, "RUNNING 만 대상으로 삼지 않는다: ${captured.bindings}")
        assertTrue(7L in captured.bindings, "관측한 version 이 조건으로 묶이지 않았다: ${captured.bindings}")
    }

    /** 지난 실패 사유를 남기면 재실행한 화면에 예전 오류가 그대로 보인다. */
    @Test
    @DisplayName("확보하면서 이전 오류 사유를 비운다")
    fun claimClearsPreviousError() {
        val captured = capture { it.claimRunning(1L) }

        assertTrue("error_message" in captured.sql, "확보가 이전 오류를 지우지 않는다: ${captured.sql}")
    }
}
