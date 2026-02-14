package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.activitylog.ActivityLog
import com.ongo.domain.activitylog.ActivityLogRepository
import com.ongo.infrastructure.persistence.jooq.Fields.ACTION
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DETAILS
import com.ongo.infrastructure.persistence.jooq.Fields.ENTITY_ID
import com.ongo.infrastructure.persistence.jooq.Fields.ENTITY_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IP_ADDRESS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_AGENT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.ACTIVITY_LOGS
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ActivityLogJooqRepository(
    private val dsl: DSLContext,
) : ActivityLogRepository {

    override fun findByUserId(
        userId: Long,
        page: Int,
        size: Int,
        action: String?,
        entityType: String?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): List<ActivityLog> {
        var condition = USER_ID.eq(userId)
        if (action != null) condition = condition.and(ACTION.eq(action))
        if (entityType != null) condition = condition.and(ENTITY_TYPE.eq(entityType))
        if (startDate != null) condition = condition.and(CREATED_AT.ge(startDate))
        if (endDate != null) condition = condition.and(CREATED_AT.lt(endDate))

        return dsl.select()
            .from(ACTIVITY_LOGS)
            .where(condition)
            .orderBy(CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toActivityLog() }
    }

    override fun countByUserId(
        userId: Long,
        action: String?,
        entityType: String?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): Long {
        var condition = USER_ID.eq(userId)
        if (action != null) condition = condition.and(ACTION.eq(action))
        if (entityType != null) condition = condition.and(ENTITY_TYPE.eq(entityType))
        if (startDate != null) condition = condition.and(CREATED_AT.ge(startDate))
        if (endDate != null) condition = condition.and(CREATED_AT.lt(endDate))

        return dsl.select(DSL.count())
            .from(ACTIVITY_LOGS)
            .where(condition)
            .fetchOne(0, Long::class.java) ?: 0L
    }

    override fun save(log: ActivityLog): ActivityLog {
        val detailsField = DSL.field("details", JSONB::class.java)
        val id = dsl.insertInto(ACTIVITY_LOGS)
            .set(USER_ID, log.userId)
            .set(ACTION, log.action)
            .set(ENTITY_TYPE, log.entityType)
            .set(ENTITY_ID, log.entityId)
            .set(detailsField, JSONB.jsonb(log.details ?: "{}"))
            .set(IP_ADDRESS, log.ipAddress)
            .set(USER_AGENT, log.userAgent)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return dsl.select()
            .from(ACTIVITY_LOGS)
            .where(ID.eq(id))
            .fetchOne()!!
            .toActivityLog()
    }

    private fun Record.toActivityLog(): ActivityLog {
        val detailsRaw = get("details")
        val details: String = when (detailsRaw) {
            is JSONB -> detailsRaw.data()
            is String -> detailsRaw
            else -> "{}"
        }
        return ActivityLog(
            id = get(ID),
            userId = get(USER_ID),
            action = get(ACTION),
            entityType = get(ENTITY_TYPE),
            entityId = get(ENTITY_ID),
            details = details,
            ipAddress = get(IP_ADDRESS),
            userAgent = get(USER_AGENT),
            createdAt = localDateTime(CREATED_AT),
        )
    }
}
