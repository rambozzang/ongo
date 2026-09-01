package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ERROR_MESSAGE
import com.ongo.infrastructure.persistence.jooq.Fields.EVENT_ID
import com.ongo.infrastructure.persistence.jooq.Fields.EVENT_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.MAX_RETRIES
import com.ongo.infrastructure.persistence.jooq.Fields.NEXT_RETRY_AT
import com.ongo.infrastructure.persistence.jooq.Fields.PROCESSED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.RETRY_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Tables.WEBHOOK_EVENTS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class WebhookEventJooqRepository(
    private val dsl: DSLContext,
) : WebhookEventRepository {

    /**
     * `INSERT ... ON CONFLICT (event_id) DO NOTHING`.
     *
     * 반환값은 영향 행수로 판정한다. 삽입되면 1, `event_id`가 이미 있으면 0이다.
     * `save()`처럼 `RETURNING`을 붙이면 충돌 시 반환 행이 없어 매핑에서 터진다.
     */
    override fun saveIfAbsent(event: WebhookEvent): Boolean =
        dsl.insertInto(WEBHOOK_EVENTS)
            .set(EVENT_ID, event.eventId)
            .set(EVENT_TYPE, event.eventType)
            .set(DSL.field("payload", String::class.java), event.payload)
            .set(STATUS, event.status)
            .set(RETRY_COUNT, event.retryCount)
            .set(MAX_RETRIES, event.maxRetries)
            .set(NEXT_RETRY_AT, event.nextRetryAt)
            .set(ERROR_MESSAGE, event.errorMessage)
            .set(PROCESSED_AT, event.processedAt)
            .onConflict(EVENT_ID)
            .doNothing()
            .execute() == 1

    /**
     * `event_id`로 처리 성공을 기록한다.
     *
     * `update(event)`는 `WHERE id = ?` 라 엔티티의 id가 필요하지만, `saveIfAbsent`는 행수만
     * 돌려주고 id를 되받지 않는다. 조회를 한 번 더 하는 대신 `event_id`로 바로 갱신한다.
     */
    override fun markProcessed(eventId: String, processedAt: LocalDateTime): Boolean =
        dsl.update(WEBHOOK_EVENTS)
            .set(STATUS, "PROCESSED")
            .set(PROCESSED_AT, processedAt)
            .where(EVENT_ID.eq(eventId))
            .execute() == 1

    override fun findByEventId(eventId: String): WebhookEvent? =
        dsl.select()
            .from(WEBHOOK_EVENTS)
            .where(EVENT_ID.eq(eventId))
            .fetchOne()
            ?.toWebhookEvent()

    /** `FOR UPDATE` 없이는 같은 이벤트의 동시 전달을 직렬화할 수 없다. */
    override fun findByEventIdForUpdate(eventId: String): WebhookEvent? =
        dsl.select()
            .from(WEBHOOK_EVENTS)
            .where(EVENT_ID.eq(eventId))
            .forUpdate()
            .fetchOne()
            ?.toWebhookEvent()

    /**
     * `PROCESSED` 는 종착 상태다. 조건을 갱신문에 남겨 **DB가 판정**하게 한다.
     * 읽고 나서 판단하면 그 사이에 완료된 이벤트를 실패로 되돌린다.
     */
    override fun updateIfNotProcessed(event: WebhookEvent): Boolean =
        dsl.update(WEBHOOK_EVENTS)
            .set(STATUS, event.status)
            .set(RETRY_COUNT, event.retryCount)
            .set(NEXT_RETRY_AT, event.nextRetryAt)
            .set(ERROR_MESSAGE, event.errorMessage)
            // `id` 가 아니라 `event_id` 로 찾는다. `saveIfAbsent` 는 영향 행수만 돌려주고 id 를
            // 되받지 않으므로, 수신 직후의 이벤트에는 id 가 없다. `event_id` 는 UNIQUE 라
            // 같은 행을 정확히 가리킨다. `markProcessed` 도 같은 이유로 event_id 를 쓴다.
            .where(EVENT_ID.eq(event.eventId))
            .and(STATUS.ne(PROCESSED))
            .execute() == 1

    /**
     * `event_type` 조건은 **성능이 아니라 소유권**을 위한 것이다. 이 테이블은 Paddle 과
     * 포트원이 공유하므로, 빼면 남의 `FAILED` 행이 Paddle 재처리기로 넘어간다.
     */
    override fun findRetryable(now: LocalDateTime, eventTypes: Set<String>): List<WebhookEvent> {
        if (eventTypes.isEmpty()) return emptyList()

        return dsl.select()
            .from(WEBHOOK_EVENTS)
            .where(STATUS.eq(FAILED))
            .and(EVENT_TYPE.`in`(eventTypes))
            .and(RETRY_COUNT.lessThan(MAX_RETRIES))
            .and(NEXT_RETRY_AT.lessOrEqual(now))
            .orderBy(NEXT_RETRY_AT.asc())
            .fetch()
            .map { it.toWebhookEvent() }
    }

    /**
     * `PENDING` → `FAILED` 전이는 **조건부 UPDATE 한 방**으로 한다.
     *
     * 조회 후 갱신으로 나누면 그 사이에 정상 처리가 끝나 `PROCESSED` 가 된 행을 다시
     * `FAILED` 로 되돌려 이미 반영된 결제를 재처리하게 된다. `WHERE status = 'PENDING'` 을
     * 갱신문에 그대로 두어 **DB가 승자를 정하게** 한다.
     *
     * `retry_count` 는 건드리지 않는다. 되살리는 시점까지 우리가 실제로 재처리를 시도한 적은
     * 없으므로 남은 재시도 횟수를 깎을 이유가 없다. 전이 후에는 기존 재시도 경로가 그대로
     * 횟수·백오프·DEAD_LETTER 를 관리한다.
     */
    override fun recoverStalePending(
        olderThan: LocalDateTime,
        retryAt: LocalDateTime,
        eventTypes: Set<String>,
        limit: Int,
    ): Int {
        // 양성 목록이 비면 **아무것도 되살리지 않는다.** 빈 IN 을 그대로 렌더링하면
        // 조건이 사라진 전체 갱신이 될 여지를 남긴다.
        if (eventTypes.isEmpty() || limit <= 0) return 0

        val candidates = DSL.select(ID)
            .from(WEBHOOK_EVENTS)
            .where(STATUS.eq(PENDING))
            .and(CREATED_AT.lessThan(olderThan))
            .and(EVENT_TYPE.`in`(eventTypes))
            .and(RETRY_COUNT.lessThan(MAX_RETRIES))
            .orderBy(CREATED_AT.asc())
            .limit(limit)

        return dsl.update(WEBHOOK_EVENTS)
            .set(STATUS, FAILED)
            .set(NEXT_RETRY_AT, retryAt)
            .set(ERROR_MESSAGE, STALE_PENDING_REASON)
            .where(ID.`in`(candidates))
            .and(STATUS.eq(PENDING))
            .execute()
    }

    override fun findDeadLettered(limit: Int): List<WebhookEvent> {
        if (limit <= 0) return emptyList()

        return dsl.select()
            .from(WEBHOOK_EVENTS)
            .where(STATUS.eq(DEAD_LETTER))
            .orderBy(CREATED_AT.desc())
            .limit(limit)
            .fetch()
            .map { it.toWebhookEvent() }
    }

    /**
     * 조건을 전부 갱신문에 담는다. 조회 후 판단하면 그 사이에 상태가 바뀐 행을 덮어쓴다.
     *
     * `retry_count` 를 `max_retries - 1` 로 맞춰 **한 번만** 다시 시도하게 한다.
     * `GREATEST(0, ...)` 로 감싸 `max_retries` 가 0 인 이상 데이터에서도 음수가 되지 않게 한다.
     */
    override fun requeueDeadLettered(
        id: Long,
        retryAt: LocalDateTime,
        eventTypes: Set<String>,
    ): Boolean {
        if (eventTypes.isEmpty()) return false

        return dsl.update(WEBHOOK_EVENTS)
            .set(STATUS, FAILED)
            .set(NEXT_RETRY_AT, retryAt)
            .set(RETRY_COUNT, DSL.greatest(DSL.inline(0), MAX_RETRIES.minus(DSL.inline(1))))
            .where(ID.eq(id))
            .and(STATUS.eq(DEAD_LETTER))
            .and(EVENT_TYPE.`in`(eventTypes))
            .execute() == 1
    }

    private companion object {
        const val PENDING = "PENDING"
        const val FAILED = "FAILED"
        const val PROCESSED = "PROCESSED"
        const val DEAD_LETTER = "DEAD_LETTER"

        /** 운영자가 로그 없이도 왜 되살아났는지 알 수 있어야 한다. */
        const val STALE_PENDING_REASON =
            "수신만 기록되고 처리가 끝나지 않은 채 방치된 이벤트라 재시도 대상으로 되살렸습니다"
    }

    private fun Record.toWebhookEvent(): WebhookEvent = WebhookEvent(
        id = get(ID),
        eventId = get(EVENT_ID),
        eventType = get(EVENT_TYPE),
        payload = get(DSL.field("payload", String::class.java)) ?: "{}",
        status = get(STATUS) ?: "PENDING",
        retryCount = get(RETRY_COUNT) ?: 0,
        maxRetries = get(MAX_RETRIES) ?: 5,
        nextRetryAt = localDateTime(NEXT_RETRY_AT),
        errorMessage = get(ERROR_MESSAGE),
        processedAt = localDateTime(PROCESSED_AT),
        createdAt = localDateTime(CREATED_AT),
    )
}
