package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.webhook.Webhook
import com.ongo.domain.webhook.WebhookRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.FAILURE_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IS_ACTIVE
import com.ongo.infrastructure.persistence.jooq.Fields.LAST_STATUS_CODE
import com.ongo.infrastructure.persistence.jooq.Fields.LAST_TRIGGERED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.SECRET
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.URL
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.WEBHOOKS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class WebhookJooqRepository(
    private val dsl: DSLContext,
) : WebhookRepository {

    companion object {
        private val EVENTS_FIELD = DSL.field("events", Array<String>::class.java)
    }

    override fun findById(id: Long): Webhook? =
        dsl.select()
            .from(WEBHOOKS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toWebhook()

    override fun findByUserId(userId: Long): List<Webhook> =
        dsl.select()
            .from(WEBHOOKS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toWebhook() }

    override fun save(webhook: Webhook): Webhook {
        val record = dsl.insertInto(WEBHOOKS)
            .set(USER_ID, webhook.userId)
            .set(NAME, webhook.name)
            .set(URL, webhook.url)
            .set(EVENTS_FIELD, webhook.events.toTypedArray())
            .set(SECRET, webhook.secret)
            .set(IS_ACTIVE, webhook.isActive)
            .returning()
            .fetchOne()!!

        return record.toWebhook()
    }

    override fun update(webhook: Webhook): Webhook {
        val record = dsl.update(WEBHOOKS)
            .set(NAME, webhook.name)
            .set(URL, webhook.url)
            .set(EVENTS_FIELD, webhook.events.toTypedArray())
            .set(SECRET, webhook.secret)
            .set(IS_ACTIVE, webhook.isActive)
            .set(LAST_TRIGGERED_AT, webhook.lastTriggeredAt)
            .set(LAST_STATUS_CODE, webhook.lastStatusCode)
            .set(FAILURE_COUNT, webhook.failureCount)
            .where(ID.eq(webhook.id))
            .returning()
            .fetchOne()!!

        return record.toWebhook()
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(WEBHOOKS)
            .where(ID.eq(id))
            .execute()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Record.toWebhook(): Webhook {
        val eventsRaw = get("events")
        val events: List<String> = when (eventsRaw) {
            is Array<*> -> (eventsRaw as Array<String>).toList()
            is java.sql.Array -> (eventsRaw.array as Array<String>).toList()
            else -> emptyList()
        }

        return Webhook(
            id = get(ID),
            userId = get(USER_ID),
            name = get(NAME),
            url = get(URL),
            events = events,
            secret = get(SECRET),
            isActive = get(IS_ACTIVE) ?: true,
            lastTriggeredAt = localDateTime(LAST_TRIGGERED_AT),
            lastStatusCode = get(LAST_STATUS_CODE),
            failureCount = get(FAILURE_COUNT) ?: 0,
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
