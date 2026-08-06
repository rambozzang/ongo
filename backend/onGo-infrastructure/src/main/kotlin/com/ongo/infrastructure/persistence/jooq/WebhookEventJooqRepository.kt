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

    override fun save(event: WebhookEvent): WebhookEvent {
        val id = dsl.insertInto(WEBHOOK_EVENTS)
            .set(EVENT_ID, event.eventId)
            .set(EVENT_TYPE, event.eventType)
            .set(DSL.field("payload", String::class.java), event.payload)
            .set(STATUS, event.status)
            .set(RETRY_COUNT, event.retryCount)
            .set(MAX_RETRIES, event.maxRetries)
            .set(NEXT_RETRY_AT, event.nextRetryAt)
            .set(ERROR_MESSAGE, event.errorMessage)
            .set(PROCESSED_AT, event.processedAt)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return event.copy(id = id)
    }

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

    override fun findRetryable(now: LocalDateTime): List<WebhookEvent> =
        dsl.select()
            .from(WEBHOOK_EVENTS)
            .where(STATUS.eq("FAILED"))
            .and(RETRY_COUNT.lessThan(MAX_RETRIES))
            .and(NEXT_RETRY_AT.lessOrEqual(now))
            .orderBy(NEXT_RETRY_AT.asc())
            .fetch()
            .map { it.toWebhookEvent() }

    override fun update(event: WebhookEvent) {
        dsl.update(WEBHOOK_EVENTS)
            .set(STATUS, event.status)
            .set(RETRY_COUNT, event.retryCount)
            .set(NEXT_RETRY_AT, event.nextRetryAt)
            .set(ERROR_MESSAGE, event.errorMessage)
            .set(PROCESSED_AT, event.processedAt)
            .where(ID.eq(event.id))
            .execute()
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
