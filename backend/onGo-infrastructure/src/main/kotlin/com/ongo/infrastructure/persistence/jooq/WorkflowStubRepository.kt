package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.workflow.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.VISUAL_WORKFLOWS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class VisualWorkflowJooqRepository(
    private val dsl: DSLContext,
) : WorkflowRepository {

    companion object {
        private val WORKFLOW_DATA = DSL.field("workflow_data", String::class.java)
        private val IS_ACTIVE = DSL.field("is_active", Boolean::class.java)
        private val LAST_TRIGGERED_AT = DSL.field("last_triggered_at", java.time.LocalDateTime::class.java)
        private val TRIGGER_COUNT = DSL.field("trigger_count", Int::class.java)
    }

    override fun findById(id: Long): VisualWorkflow? =
        dsl.select()
            .from(VISUAL_WORKFLOWS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toEntity()

    override fun findByUserId(userId: Long): List<VisualWorkflow> =
        dsl.select()
            .from(VISUAL_WORKFLOWS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun save(workflow: VisualWorkflow): VisualWorkflow {
        val id = dsl.insertInto(VISUAL_WORKFLOWS)
            .set(USER_ID, workflow.userId)
            .set(NAME, workflow.name)
            .set(DESCRIPTION, workflow.description)
            .set(WORKFLOW_DATA, workflow.workflowData)
            .set(IS_ACTIVE, workflow.isActive)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(workflow: VisualWorkflow): VisualWorkflow {
        dsl.update(VISUAL_WORKFLOWS)
            .set(NAME, workflow.name)
            .set(DESCRIPTION, workflow.description)
            .set(WORKFLOW_DATA, workflow.workflowData)
            .set(IS_ACTIVE, workflow.isActive)
            .set(LAST_TRIGGERED_AT, workflow.lastTriggeredAt)
            .set(TRIGGER_COUNT, workflow.triggerCount)
            .set(UPDATED_AT, java.time.LocalDateTime.now())
            .where(ID.eq(workflow.id))
            .execute()

        return findById(workflow.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(VISUAL_WORKFLOWS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toEntity(): VisualWorkflow = VisualWorkflow(
        id = get(ID),
        userId = get(USER_ID),
        name = get(NAME),
        description = get(DESCRIPTION),
        workflowData = get(WORKFLOW_DATA) ?: "{}",
        isActive = get(IS_ACTIVE) ?: false,
        lastTriggeredAt = localDateTime(LAST_TRIGGERED_AT),
        triggerCount = get(TRIGGER_COUNT) ?: 0,
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
