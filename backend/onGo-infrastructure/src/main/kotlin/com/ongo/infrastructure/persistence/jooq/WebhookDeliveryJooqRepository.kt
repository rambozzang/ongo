package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.webhook.WebhookDelivery
import com.ongo.domain.webhook.WebhookDeliveryRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DELIVERY_ATTEMPT_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.DELIVERY_LAST_ERROR
import com.ongo.infrastructure.persistence.jooq.Fields.DELIVERY_LEASE_OWNER
import com.ongo.infrastructure.persistence.jooq.Fields.DELIVERY_LEASE_UNTIL
import com.ongo.infrastructure.persistence.jooq.Fields.DELIVERY_NEXT_ATTEMPT_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DELIVERY_STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.DELIVERY_STATUS_CODE
import com.ongo.infrastructure.persistence.jooq.Fields.EVENT_KEY
import com.ongo.infrastructure.persistence.jooq.Fields.EVENT_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PAYLOAD
import com.ongo.infrastructure.persistence.jooq.Fields.RESPONSE_BODY
import com.ongo.infrastructure.persistence.jooq.Fields.SENT_AT
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.WEBHOOK_ID
import com.ongo.infrastructure.persistence.jooq.Tables.WEBHOOK_DELIVERIES
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class WebhookDeliveryJooqRepository(
    private val dsl: DSLContext,
) : WebhookDeliveryRepository {

    override fun save(delivery: WebhookDelivery): WebhookDelivery {
        val id = dsl.insertInto(WEBHOOK_DELIVERIES)
            .set(WEBHOOK_ID, delivery.webhookId)
            .set(EVENT_KEY, delivery.eventKey)
            .set(EVENT_TYPE, delivery.eventType)
            .set(PAYLOAD, delivery.payload)
            .set(DELIVERY_STATUS, delivery.status)
            .set(DELIVERY_ATTEMPT_COUNT, delivery.attemptCount)
            .set(DELIVERY_NEXT_ATTEMPT_AT, delivery.nextAttemptAt ?: LocalDateTime.now())
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return findById(id)!!
    }

    override fun saveIfAbsent(delivery: WebhookDelivery): Boolean =
        dsl.insertInto(WEBHOOK_DELIVERIES)
            .set(WEBHOOK_ID, delivery.webhookId)
            .set(EVENT_KEY, delivery.eventKey)
            .set(EVENT_TYPE, delivery.eventType)
            .set(PAYLOAD, delivery.payload)
            .set(DELIVERY_STATUS, delivery.status)
            .set(DELIVERY_ATTEMPT_COUNT, delivery.attemptCount)
            .set(DELIVERY_NEXT_ATTEMPT_AT, delivery.nextAttemptAt ?: LocalDateTime.now())
            .onConflict(WEBHOOK_ID, EVENT_KEY)
            .doNothing()
            .execute() == 1

    override fun findByWebhookId(webhookId: Long, limit: Int): List<WebhookDelivery> =
        dsl.select().from(WEBHOOK_DELIVERIES)
            .where(WEBHOOK_ID.eq(webhookId))
            .orderBy(CREATED_AT.desc())
            .limit(limit.coerceIn(1, 200))
            .fetch().map { it.toDelivery() }

    override fun findDue(now: LocalDateTime, limit: Int): List<WebhookDelivery> =
        dsl.select().from(WEBHOOK_DELIVERIES)
            .where(DELIVERY_STATUS.`in`("PENDING", "FAILED"))
            .and(DELIVERY_NEXT_ATTEMPT_AT.le(now))
            .and(DELIVERY_LEASE_UNTIL.isNull.or(DELIVERY_LEASE_UNTIL.lt(now)))
            .orderBy(DELIVERY_NEXT_ATTEMPT_AT.asc())
            .limit(limit.coerceIn(1, 500))
            .fetch().map { it.toDelivery() }

    override fun claim(id: Long, owner: String, now: LocalDateTime, leaseUntil: LocalDateTime): WebhookDelivery? {
        val changed = dsl.update(WEBHOOK_DELIVERIES)
            .set(DELIVERY_LEASE_OWNER, owner)
            .set(DELIVERY_LEASE_UNTIL, leaseUntil)
            .set(DELIVERY_ATTEMPT_COUNT, DELIVERY_ATTEMPT_COUNT.plus(1))
            .where(ID.eq(id))
            .and(DELIVERY_STATUS.`in`("PENDING", "FAILED"))
            .and(DELIVERY_NEXT_ATTEMPT_AT.le(now))
            .and(DELIVERY_LEASE_UNTIL.isNull.or(DELIVERY_LEASE_UNTIL.lt(now)))
            .execute()
        return if (changed == 1) findById(id) else null
    }

    override fun updateOwned(delivery: WebhookDelivery, owner: String): Boolean =
        dsl.update(WEBHOOK_DELIVERIES)
            .set(DELIVERY_STATUS, delivery.status)
            .set(DELIVERY_NEXT_ATTEMPT_AT, delivery.nextAttemptAt)
            .set(DELIVERY_LEASE_OWNER, DSL.`val`(null as String?))
            .set(DELIVERY_LEASE_UNTIL, DSL.`val`(null as LocalDateTime?))
            .set(DELIVERY_STATUS_CODE, delivery.statusCode)
            .set(RESPONSE_BODY, delivery.responseBody)
            .set(SENT_AT, delivery.sentAt)
            .set(DELIVERY_LAST_ERROR, delivery.lastError)
            .where(ID.eq(delivery.id))
            .and(DELIVERY_LEASE_OWNER.eq(owner))
            .execute() == 1

    override fun findById(id: Long): WebhookDelivery? =
        dsl.select().from(WEBHOOK_DELIVERIES).where(ID.eq(id)).fetchOne()?.toDelivery()

    override fun requeue(id: Long, now: LocalDateTime): Boolean =
        dsl.update(WEBHOOK_DELIVERIES)
            .set(DELIVERY_STATUS, "PENDING")
            .set(DELIVERY_NEXT_ATTEMPT_AT, now)
            .set(DELIVERY_LEASE_OWNER, null as String?)
            .set(DELIVERY_LEASE_UNTIL, null as LocalDateTime?)
            .set(DELIVERY_LAST_ERROR, null as String?)
            .where(ID.eq(id))
            .execute() == 1

    private fun Record.toDelivery() = WebhookDelivery(
        id = get(ID), webhookId = get(WEBHOOK_ID), eventKey = get(EVENT_KEY),
        eventType = get(EVENT_TYPE), payload = get(PAYLOAD, String::class.java) ?: "{}",
        status = get(DELIVERY_STATUS) ?: "PENDING",
        attemptCount = get(DELIVERY_ATTEMPT_COUNT) ?: 0,
        nextAttemptAt = localDateTime(DELIVERY_NEXT_ATTEMPT_AT),
        leaseOwner = get(DELIVERY_LEASE_OWNER), leaseUntil = localDateTime(DELIVERY_LEASE_UNTIL),
        statusCode = get(DELIVERY_STATUS_CODE), responseBody = get(RESPONSE_BODY),
        sentAt = localDateTime(SENT_AT), lastError = get(DELIVERY_LAST_ERROR),
        createdAt = localDateTime(CREATED_AT), updatedAt = localDateTime(UPDATED_AT),
    )
}
