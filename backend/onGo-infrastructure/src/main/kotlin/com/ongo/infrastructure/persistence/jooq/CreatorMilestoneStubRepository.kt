package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.creatormilestone.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class CreatorMilestoneJooqRepository(
    private val dsl: DSLContext,
) : CreatorMilestoneRepository {

    companion object {
        private val TABLE = DSL.table("creator_milestones")
        private val TYPE = DSL.field("type", String::class.java)
        private val TITLE = DSL.field("title", String::class.java)
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val TARGET_VALUE = DSL.field("target_value", Long::class.java)
        private val CURRENT_VALUE = DSL.field("current_value", Long::class.java)
        private val PROGRESS = DSL.field("progress", BigDecimal::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val ACHIEVED_AT = DSL.field("achieved_at", LocalDateTime::class.java)
        private val TARGET_DATE = DSL.field("target_date", java.time.LocalDate::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<CreatorMilestone> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch { it.toMilestone() }

    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<CreatorMilestone> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(STATUS.eq(status))
            .fetch { it.toMilestone() }

    override fun findById(id: Long): CreatorMilestone? =
        dsl.select().from(TABLE)
            .where(ID.eq(id))
            .fetchOne()?.toMilestone()

    override fun save(milestone: CreatorMilestone): CreatorMilestone {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, milestone.workspaceId)
            .set(TYPE, milestone.type)
            .set(TITLE, milestone.title)
            .set(DESCRIPTION, milestone.description)
            .set(TARGET_VALUE, milestone.targetValue)
            .set(CURRENT_VALUE, milestone.currentValue)
            .set(PROGRESS, milestone.progress)
            .set(PLATFORM, milestone.platform)
            .set(STATUS, milestone.status)
            .set(ACHIEVED_AT, milestone.achievedAt)
            .set(TARGET_DATE, milestone.targetDate)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun update(milestone: CreatorMilestone): CreatorMilestone {
        dsl.update(TABLE)
            .set(TYPE, milestone.type)
            .set(TITLE, milestone.title)
            .set(DESCRIPTION, milestone.description)
            .set(TARGET_VALUE, milestone.targetValue)
            .set(CURRENT_VALUE, milestone.currentValue)
            .set(PROGRESS, milestone.progress)
            .set(PLATFORM, milestone.platform)
            .set(STATUS, milestone.status)
            .set(ACHIEVED_AT, milestone.achievedAt)
            .set(TARGET_DATE, milestone.targetDate)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(milestone.id))
            .execute()

        return findById(milestone.id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toMilestone(): CreatorMilestone =
        CreatorMilestone(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            type = get(TYPE),
            title = get(TITLE),
            description = get(DESCRIPTION),
            targetValue = get(TARGET_VALUE) ?: 0,
            currentValue = get(CURRENT_VALUE) ?: 0,
            progress = get(PROGRESS) ?: BigDecimal.ZERO,
            platform = get(PLATFORM),
            status = get(STATUS) ?: "PENDING",
            achievedAt = localDateTime(ACHIEVED_AT),
            targetDate = localDate(TARGET_DATE),
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
            updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
        )
}
