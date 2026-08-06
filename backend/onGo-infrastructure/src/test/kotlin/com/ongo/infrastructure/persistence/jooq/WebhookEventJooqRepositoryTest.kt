package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.webhook.WebhookEvent
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
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

    /** @param affectedRows 1이면 삽입 성공, 0이면 event_id 충돌로 DO NOTHING */
    private fun repository(affectedRows: Int): WebhookEventJooqRepository {
        val provider = MockDataProvider { ctx ->
            executed += ctx.sql()
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

    @Test
    @DisplayName("기존 save는 그대로 RETURNING을 쓴다 — Paddle 경로 비회귀 대조군")
    fun legacySaveStillUsesReturning() {
        val provider = MockDataProvider { ctx ->
            executed += ctx.sql()
            val result = DSL.using(SQLDialect.POSTGRES).newResult(Fields.ID)
            result.add(DSL.using(SQLDialect.POSTGRES).newRecord(Fields.ID).also { it.set(Fields.ID, 1L) })
            arrayOf(MockResult(1, result))
        }
        val repo = WebhookEventJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES))

        repo.save(event())

        val sql = executed.single().lowercase()
        assertTrue(sql.contains("returning"), "기존 save 동작이 바뀌면 Paddle이 깨진다: $sql")
        assertFalse(sql.contains("on conflict"), "기존 save에 ON CONFLICT가 붙으면 안 된다: $sql")
    }
}
