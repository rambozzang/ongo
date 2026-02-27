package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.analytics.LiveAlert
import com.ongo.domain.analytics.LiveAlertRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IS_READ
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.LIVE_DASHBOARD_ALERTS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class LiveAlertJooqRepository(
    private val dsl: DSLContext,
) : LiveAlertRepository {

    companion object {
        private val typeField = DSL.field("type", String::class.java)
        private val messageField = DSL.field("message", String::class.java)
        private val severityField = DSL.field("severity", String::class.java)
    }

    override fun findByUserId(userId: Long): List<LiveAlert> =
        dsl.select()
            .from(LIVE_DASHBOARD_ALERTS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toAlert() }

    override fun findById(id: Long): LiveAlert? =
        dsl.select()
            .from(LIVE_DASHBOARD_ALERTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toAlert()

    override fun markRead(id: Long) {
        dsl.update(LIVE_DASHBOARD_ALERTS)
            .set(IS_READ, true)
            .where(ID.eq(id))
            .execute()
    }

    override fun save(alert: LiveAlert): LiveAlert {
        val id = dsl.insertInto(LIVE_DASHBOARD_ALERTS)
            .set(USER_ID, alert.userId)
            .set(typeField, alert.type)
            .set(messageField, alert.message)
            .set(severityField, alert.severity)
            .set(IS_READ, alert.isRead)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    private fun Record.toAlert(): LiveAlert = LiveAlert(
        id = get(ID),
        userId = get(USER_ID),
        type = get(typeField) ?: "",
        message = get(messageField) ?: "",
        severity = get(severityField) ?: "INFO",
        isRead = get(IS_READ) ?: false,
        createdAt = localDateTime(CREATED_AT),
    )
}
