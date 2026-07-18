package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.audit.AuditEvent
import com.ongo.domain.ugc.audit.AuditEventRepository
import com.ongo.infrastructure.persistence.jooq.Fields.ACTION
import com.ongo.infrastructure.persistence.jooq.Fields.ACTOR_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CAMPAIGN_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DETAIL
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.RESOURCE_ID
import com.ongo.infrastructure.persistence.jooq.Fields.RESOURCE_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_AUDIT_EVENTS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class JooqAuditEventRepository(
    private val dsl: DSLContext,
) : AuditEventRepository {

    override fun save(event: AuditEvent): AuditEvent {
        val id = dsl.insertInto(UGC_AUDIT_EVENTS)
            .set(WORKSPACE_ID, event.workspaceId)
            .set(CAMPAIGN_ID, event.campaignId)
            .set(ACTOR_ID, event.actorId)
            .set(ACTION, event.action)
            .set(RESOURCE_TYPE, event.resourceType)
            .set(RESOURCE_ID, event.resourceId)
            .set(DETAIL, event.detail)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return dsl.select().from(UGC_AUDIT_EVENTS).where(ID.eq(id)).fetchOne()!!.toEvent()
    }

    override fun findByCampaignId(campaignId: Long, offset: Int, limit: Int): List<AuditEvent> =
        dsl.select().from(UGC_AUDIT_EVENTS)
            .where(CAMPAIGN_ID.eq(campaignId))
            .orderBy(CREATED_AT.desc())
            .limit(limit).offset(offset)
            .fetch().map { it.toEvent() }

    override fun countByCampaignId(campaignId: Long): Long =
        dsl.selectCount().from(UGC_AUDIT_EVENTS).where(CAMPAIGN_ID.eq(campaignId)).fetchOne(0, Long::class.java) ?: 0L

    private fun Record.toEvent(): AuditEvent = AuditEvent(
        id = get(ID),
        workspaceId = get(WORKSPACE_ID),
        campaignId = get(CAMPAIGN_ID),
        actorId = get(ACTOR_ID),
        action = get(ACTION),
        resourceType = get(RESOURCE_TYPE),
        resourceId = get(RESOURCE_ID),
        detail = get(DETAIL),
        createdAt = localDateTime(CREATED_AT),
    )
}
