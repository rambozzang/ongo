package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.webhook.WebhookEvent
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
import java.time.LocalDateTime

/**
 * 웹훅 멱등 게이트가 **실제 발행하는 SQL**과 반환값을 확인한다.
 *
 * 검사-후-삽입(`findByEventId` → `save`)은 경합에서 두 트랜잭션이 모두 통과한다.
 * 원자적 `INSERT ... ON CONFLICT DO NOTHING` 이어야 하고, 삽입 성공(1)과 중복(0)을
 * 행수로 구분해야 한다. 기존 `save()`처럼 `RETURNING`을 쓰면 충돌 시 행이 없어 NPE가 난다.
 */
class WebhookEventJooqRepositoryTest {

    private val executed = mutableListOf<String>()
    private val bindings = mutableListOf<Any?>()

    /** @param affectedRows 1이면 삽입 성공, 0이면 event_id 충돌로 DO NOTHING */
    private fun repository(affectedRows: Int): WebhookEventJooqRepository {
        val provider = MockDataProvider { ctx ->
            executed += ctx.sql()
            bindings.addAll(ctx.bindings().toList())
            arrayOf(MockResult(affectedRows))
        }
        return WebhookEventJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES))
    }

    private fun event(eventId: String = "portone:webhook-1") = WebhookEvent(
        eventId = eventId,
        eventType = "Transaction.Paid",
        payload = """{"type":"Transaction.Paid"}""",
    )

    @Test
    @DisplayName("saveIfAbsent는 ON CONFLICT DO NOTHING SQL을 발행한다")
    fun rendersOnConflictDoNothing() {
        repository(affectedRows = 1).saveIfAbsent(event())

        val sql = executed.single().lowercase()
        assertTrue(sql.contains("insert into"), "삽입이어야 한다: $sql")
        assertTrue(sql.contains("webhook_events"), "webhook_events 대상이어야 한다: $sql")
        assertTrue(sql.contains("on conflict"), "ON CONFLICT가 없다: $sql")
        assertTrue(sql.contains("do nothing"), "DO NOTHING이 없다: $sql")
        assertTrue(sql.contains("event_id"), "event_id 충돌 대상이 명시돼야 한다: $sql")
    }

    @Test
    @DisplayName("saveIfAbsent는 RETURNING을 쓰지 않는다 — 충돌 시 행이 없어 NPE가 난다")
    fun doesNotUseReturning() {
        repository(affectedRows = 1).saveIfAbsent(event())

        assertFalse(executed.single().lowercase().contains("returning"), "RETURNING을 쓰면 안 된다")
    }

    @Test
    @DisplayName("삽입에 성공하면(1행) true를 반환한다")
    fun returnsTrueWhenInserted() {
        assertTrue(repository(affectedRows = 1).saveIfAbsent(event()))
    }

    @Test
    @DisplayName("event_id가 이미 있으면(0행) false를 반환한다 — 중복 수신")
    fun returnsFalseWhenDuplicate() {
        assertFalse(repository(affectedRows = 0).saveIfAbsent(event()))
    }

    @Test
    @DisplayName("markProcessed는 event_id로 status와 processed_at을 갱신한다")
    fun markProcessedUpdatesByEventId() {
        repository(affectedRows = 1).markProcessed("portone:webhook-1", LocalDateTime.now())

        val sql = executed.single().lowercase()
        assertTrue(sql.contains("update"), "갱신이어야 한다: $sql")
        assertTrue(sql.contains("webhook_events"), "webhook_events 대상이어야 한다: $sql")
        assertTrue(sql.contains("status"), "status를 갱신해야 한다: $sql")
        assertTrue(sql.contains("processed_at"), "processed_at을 갱신해야 한다: $sql")
        // 삽입 시 id를 되받지 않으므로 event_id 로 찾아야 한다
        assertTrue(sql.contains("event_id"), "event_id 조건이어야 한다: $sql")
    }

    @Test
    @DisplayName("markProcessed는 갱신 행이 없으면 false를 반환한다")
    fun markProcessedReturnsFalseWhenNoRow() {
        assertFalse(repository(affectedRows = 0).markProcessed("portone:missing", LocalDateTime.now()))
        assertTrue(repository(affectedRows = 1).markProcessed("portone:webhook-1", LocalDateTime.now()))
    }

    // ── 동시 전달 직렬화 · PROCESSED 종착 보장 ────────────────────────────────

    @Test
    @DisplayName("findByEventIdForUpdate 는 행을 잠근다 — 잠그지 않으면 동시 전달이 함께 처리된다")
    fun findByEventIdForUpdateLocksTheRow() {
        selectRepository().findByEventIdForUpdate("evt_1")

        val sql = executed.single().lowercase()
        assertTrue(sql.contains("for update"), "행 잠금이 없다: $sql")
        assertTrue(sql.contains("event_id"), "event_id 로 찾아야 한다: $sql")
    }

    @Test
    @DisplayName("updateIfNotProcessed 는 PROCESSED 행을 건드리지 않는다 — 완료를 실패로 되돌리면 이중 반영")
    fun updateIfNotProcessedGuardsTerminalState() {
        repository(affectedRows = 1).updateIfNotProcessed(
            event().copy(id = 5L, status = "FAILED", retryCount = 2),
        )

        val sql = executed.single().lowercase()
        val where = sql.substringAfter(" where ")
        assertTrue(where.contains("status"), "갱신 조건에 status 가 없다: $sql")
        assertTrue(bindings.contains("PROCESSED"), "PROCESSED 제외 조건이 바인딩되지 않았다: $bindings")
        // 실패 기록이 완료 시각을 지우면 안 된다.
        assertFalse(
            sql.substringBefore(" where ").contains("processed_at"),
            "실패 기록이 processed_at 을 덮는다: $sql",
        )
    }

    @Test
    @DisplayName("updateIfNotProcessed 는 갱신 행이 없으면 false — 이미 완료됐다는 뜻")
    fun updateIfNotProcessedReportsNoOp() {
        assertFalse(repository(affectedRows = 0).updateIfNotProcessed(event().copy(id = 5L)))
        assertTrue(repository(affectedRows = 1).updateIfNotProcessed(event().copy(id = 5L)))
    }

    // ── DEAD_LETTER 조회·재큐잉 ──────────────────────────────────────────────
    //
    // 재시도를 모두 소진한 행은 어떤 스케줄러도 다시 집지 않는다. 운영자가 보고 되돌려야만
    // 결제·환불이 반영된다.

    @Test
    @DisplayName("DEAD_LETTER 조회는 그 상태만 최근 순으로 고른다")
    fun findDeadLetteredSelectsOnlyThatStatus() {
        selectRepository().findDeadLettered(50)

        val sql = executed.single().lowercase()
        assertTrue(sql.startsWith("select"), "조회여야 한다: $sql")
        assertTrue(sql.contains("status"), "상태 조건이 없다: $sql")
        assertTrue(bindings.contains("DEAD_LETTER"), "DEAD_LETTER 만 골라야 한다: $bindings")
        assertTrue(sql.contains("order by created_at desc"), "최근 순이어야 한다: $sql")
        assertTrue(sql.contains("fetch next"), "상한이 없으면 전체를 끌어온다: $sql")
    }

    @Test
    @DisplayName("DEAD_LETTER 조회 상한이 0 이하면 SQL 을 발행하지 않는다")
    fun findDeadLetteredIsClosedWhenLimitIsNotPositive() {
        assertTrue(selectRepository().findDeadLettered(0).isEmpty())
        assertTrue(executed.isEmpty(), "상한이 0인데 조회를 발행했다: $executed")
    }

    /**
     * 재큐잉은 운영자의 명시적 조치지만, **무엇을 되돌릴 수 있는지는 DB가 판정해야 한다.**
     * 읽고 나서 분기하면 그 사이에 완료된 이벤트를 되살려 반영된 결제를 다시 처리한다.
     */
    @Test
    @DisplayName("재큐잉은 DEAD_LETTER·허용 타입 조건을 갱신문에 담는다 — 임의 상태 전이 차단")
    fun requeueCarriesItsGuards() {
        repository(affectedRows = 1)
            .requeueDeadLettered(7L, LocalDateTime.of(2026, 8, 28, 4, 0), staleTypes)

        val sql = executed.single().lowercase()
        val where = sql.substringAfter(" where ")
        assertTrue(sql.startsWith("update"), "갱신이어야 한다: $sql")
        assertTrue(where.contains("id"), "대상 지정이 없다: $sql")
        assertTrue(where.contains("status"), "DEAD_LETTER 조건이 없다 — PROCESSED 도 되살아난다: $sql")
        assertTrue(where.contains("event_type"), "타입 제한이 없다 — 남의 이벤트도 되살아난다: $sql")
        assertTrue(bindings.contains("DEAD_LETTER"), "DEAD_LETTER 조건이 바인딩되지 않았다: $bindings")
        assertTrue(bindings.contains("FAILED"), "FAILED 로 옮겨야 재시도 대상이 된다: $bindings")
        assertTrue(bindings.containsAll(staleTypes.toList()), "허용 타입이 바인딩되지 않았다: $bindings")
    }

    @Test
    @DisplayName("재큐잉은 재시도를 한 번만 허용한다 — max_retries - 1 로 맞춘다")
    fun requeueGrantsExactlyOneMoreAttempt() {
        repository(affectedRows = 1).requeueDeadLettered(7L, LocalDateTime.now(), staleTypes)

        val setClause = executed.single().lowercase().substringBefore(" where ")
        assertTrue(setClause.contains("retry_count"), "재시도 횟수를 조정하지 않으면 다시 안 잡힌다: $setClause")
        assertTrue(setClause.contains("max_retries"), "max_retries 기준이어야 한다: $setClause")
        // 0 으로 되돌리면 자동 재시도 한 주기가 통째로 다시 돈다.
        assertFalse(
            Regex("retry_count\\s*=\\s*0").containsMatchIn(setClause),
            "재시도 예산을 통째로 초기화한다: $setClause",
        )
    }

    @Test
    @DisplayName("재큐잉은 갱신 행이 없으면 false — 성공으로 보고하지 않는다")
    fun requeueReportsRejection() {
        assertFalse(repository(affectedRows = 0).requeueDeadLettered(7L, LocalDateTime.now(), staleTypes))
        assertTrue(repository(affectedRows = 1).requeueDeadLettered(7L, LocalDateTime.now(), staleTypes))
    }

    @Test
    @DisplayName("허용 타입이 비면 재큐잉 SQL 을 발행하지 않는다 — 조건 없는 전체 갱신 방지")
    fun requeueIsClosedWhenScopeIsEmpty() {
        assertFalse(repository(affectedRows = 9).requeueDeadLettered(7L, LocalDateTime.now(), emptySet()))
        assertTrue(executed.isEmpty(), "범위가 비었는데 SQL 을 발행했다: $executed")
    }

    // ── 재시도 대상 조회의 소유권 격리 ────────────────────────────────────────
    //
    // webhook_events 는 Paddle 과 포트원이 공유한다. 상태만으로 고르면 포트원의 FAILED 행이
    // Paddle 재처리기로 넘어가 no-op 한 뒤 PROCESSED 로 찍힌다.

    /** 조회는 갱신 행수가 아니라 **빈 결과 집합**을 돌려줘야 매핑이 성립한다. */
    private fun selectRepository(): WebhookEventJooqRepository {
        val provider = MockDataProvider { ctx ->
            executed += ctx.sql()
            bindings.addAll(ctx.bindings().toList())
            arrayOf(MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult(Fields.ID)))
        }
        return WebhookEventJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES))
    }

    @Test
    @DisplayName("재시도 조회는 상태뿐 아니라 이벤트 타입으로도 좁힌다 — PG 간 격리")
    fun findRetryableFiltersByOwningEventTypes() {
        val types = setOf("transaction.completed", "subscription.updated")

        selectRepository().findRetryable(LocalDateTime.of(2026, 8, 28, 3, 0), types)

        val sql = executed.single().lowercase()
        assertTrue(sql.contains("event_type in"), "타입 조건이 없으면 포트원 행이 딸려 온다: $sql")
        assertTrue(sql.contains("status"), "status 조건이 사라졌다: $sql")
        assertTrue(sql.contains("next_retry_at"), "백오프 조건이 사라졌다: $sql")
        assertTrue(bindings.containsAll(types.toList()), "지정한 타입이 바인딩되지 않았다: $bindings")
        // 포트원 타입은 바인딩 자체에 없어야 한다.
        assertFalse(bindings.contains("Transaction.Paid"), "포트원 타입이 조회 조건에 있다: $bindings")
    }

    @Test
    @DisplayName("재시도 조회도 타입 목록이 비면 SQL 을 발행하지 않는다")
    fun findRetryableIsClosedWhenScopeIsEmpty() {
        val result = selectRepository().findRetryable(LocalDateTime.now(), emptySet())

        assertTrue(result.isEmpty())
        assertTrue(executed.isEmpty(), "범위가 비었는데 조회를 발행했다: $executed")
    }

    // ── 방치된 PENDING 되살리기 ──────────────────────────────────────────────
    //
    // 수신 기록은 업무 트랜잭션과 분리해 먼저 커밋된다. 그 뒤 프로세스가 죽으면 PENDING 행만
    // 남는데, findRetryable 은 status = 'FAILED' 만 고르므로 그 행은 영원히 재처리되지 않는다.

    private val staleTypes = setOf("transaction.completed", "subscription.created")

    private fun recover(
        affectedRows: Int,
        types: Set<String> = staleTypes,
        limit: Int = 50,
    ): Int = repository(affectedRows).recoverStalePending(
        olderThan = LocalDateTime.of(2026, 8, 28, 3, 0),
        retryAt = LocalDateTime.of(2026, 8, 28, 3, 30),
        eventTypes = types,
        limit = limit,
    )

    @Test
    @DisplayName("되살리기는 오래된 PENDING 중 지정 타입만 상한만큼 고른다")
    fun recoverSelectsOnlyStalePendingOfGivenTypes() {
        recover(affectedRows = 2)

        val sql = executed.single().lowercase()
        assertTrue(sql.startsWith("update"), "갱신이어야 한다: $sql")
        assertTrue(sql.contains("webhook_events"), "webhook_events 대상이어야 한다: $sql")
        assertTrue(sql.contains("next_retry_at"), "재시도 시각이 없으면 findRetryable 이 못 잡는다: $sql")
        assertTrue(sql.contains("created_at"), "나이 기준이 없으면 처리 중인 수신을 가로챈다: $sql")
        // 같은 테이블을 포트원과 공유한다. 타입 제한이 빠지면 남의 행이 Paddle 재처리기로 간다.
        assertTrue(sql.contains("event_type"), "이벤트 타입 제한이 없다: $sql")
        assertTrue(sql.contains("retry_count"), "max_retries 를 넘긴 행까지 되살리면 안 된다: $sql")
        // POSTGRES 방언에서 jOOQ 는 limit 을 `fetch next ? rows only` 로 렌더링한다.
        assertTrue(sql.contains("fetch next"), "상한이 없으면 적체가 한 틱에 쏟아진다: $sql")

        assertTrue(bindings.contains("FAILED"), "FAILED 로 옮겨야 재시도 대상이 된다: $bindings")
        assertTrue(bindings.containsAll(staleTypes.toList()), "지정한 타입이 바인딩되지 않았다: $bindings")
        // jOOQ 가 상한을 Int/Long 중 무엇으로 바인딩하든 값 자체가 넘어갔는지만 본다.
        assertTrue(bindings.any { it?.toString() == "50" }, "상한이 바인딩되지 않았다: $bindings")
    }

    @Test
    @DisplayName("되살리기 갱신문에도 PENDING 조건이 남는다 — 경합에서 PROCESSED 를 되돌리면 안 된다")
    fun recoverKeepsPendingGuardOnTheUpdateItself() {
        recover(affectedRows = 1)

        // 후보 조회(서브셀렉트)와 갱신문 두 곳 모두에 PENDING 조건이 있어야 한다.
        // 갱신문에서 빠지면 조회~갱신 사이에 정상 처리를 마친 행을 FAILED 로 되돌려
        // 이미 반영된 결제를 다시 처리하게 된다.
        assertEquals(
            2,
            bindings.count { it == "PENDING" },
            "갱신문의 PENDING 조건이 사라졌다: $bindings",
        )
    }

    @Test
    @DisplayName("되살리기는 retry_count 를 건드리지 않는다 — 남은 재시도 횟수를 깎으면 안 된다")
    fun recoverDoesNotConsumeRetryBudget() {
        recover(affectedRows = 1)

        val setClause = executed.single().lowercase().substringBefore(" where ")
        assertFalse(setClause.contains("retry_count"), "SET 절이 retry_count 를 바꾼다: $setClause")
    }

    @Test
    @DisplayName("되살린 행 수를 그대로 돌려준다")
    fun recoverReturnsAffectedRows() {
        assertEquals(3, recover(affectedRows = 3))
    }

    @Test
    @DisplayName("타입 목록이 비거나 상한이 0 이면 SQL 을 발행하지 않는다 — 조건 없는 전체 갱신 방지")
    fun recoverIsClosedWhenScopeIsEmpty() {
        assertEquals(0, recover(affectedRows = 9, types = emptySet()))
        assertEquals(0, recover(affectedRows = 9, limit = 0))
        assertTrue(executed.isEmpty(), "범위가 비었는데 SQL 을 발행했다: $executed")
    }

    /**
     * **모든 상태 전이가 대상을 좁힌다.**
     *
     * 예전에는 `update(event)` 가 `WHERE id = ?` 로 행 전체를 무조건 덮어썼다. 낡은 스냅샷이
     * `PROCESSED` 를 되살리면 이미 반영된 결제·환불이 다시 처리된다. 그래서 그 메서드를
     * 계약에서 없앴고, 남은 갱신 경로가 전부 조건을 SQL 에 담는지 여기서 고정한다.
     *
     * 새 갱신 메서드를 추가하면서 이 목록에 넣지 않으면 아무도 모르게 같은 구멍이 생긴다.
     */
    @Test
    @DisplayName("모든 상태 전이는 조건을 SQL 에 담는다 — id 기준 무조건 덮어쓰기가 없다")
    fun everyStateTransitionCarriesItsGuard() {
        val transitions: List<Pair<String, WebhookEventJooqRepository.() -> Unit>> = listOf(
            "markProcessed" to { markProcessed("evt_1", LocalDateTime.now()) },
            "updateIfNotProcessed" to { updateIfNotProcessed(event().copy(id = 1L, status = "FAILED")) },
            "recoverStalePending" to {
                recoverStalePending(LocalDateTime.now(), LocalDateTime.now(), staleTypes, 10)
            },
        )

        transitions.forEach { (name, transition) ->
            executed.clear()
            repository(affectedRows = 1).transition()

            val sql = executed.single().lowercase()
            assertTrue(sql.startsWith("update"), "$name 이 갱신문이 아니다: $sql")
            val where = sql.substringAfter(" where ")
            assertTrue(where.isNotEmpty() && where != sql, "$name 에 WHERE 가 없다: $sql")
            // 대상은 event_id 로 좁히거나(상태 전이) 조건부 후보 선택이어야 한다.
            assertTrue(
                where.contains("event_id") || where.contains("status"),
                "$name 이 대상을 좁히지 않는다 — 낡은 스냅샷이 PROCESSED 를 되살릴 수 있다: $sql",
            )
        }
    }
}
