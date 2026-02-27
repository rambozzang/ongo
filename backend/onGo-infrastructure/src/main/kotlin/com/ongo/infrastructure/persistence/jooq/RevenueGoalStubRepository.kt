package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.revenuegoal.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Tables.REVENUE_GOALS
import com.ongo.infrastructure.persistence.jooq.Tables.REVENUE_GOAL_MILESTONES
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class RevenueGoalJooqRepository(
    private val dsl: DSLContext,
) : RevenueGoalRepository {

    companion object {
        private val WORKSPACE_ID = DSL.field("workspace_id", Long::class.java)
        private val TARGET_AMOUNT = DSL.field("target_amount", Long::class.java)
        private val CURRENT_AMOUNT = DSL.field("current_amount", Long::class.java)
        private val CURRENCY = DSL.field("currency", String::class.java)
        private val PERIOD = DSL.field("period", String::class.java)
        private val PROGRESS = DSL.field("progress", Int::class.java)
        private val START_DATE = DSL.field("start_date", java.time.LocalDate::class.java)
        private val END_DATE = DSL.field("end_date", java.time.LocalDate::class.java)
    }

    override fun findById(id: Long): RevenueGoal? =
        dsl.select()
            .from(REVENUE_GOALS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toEntity()

    override fun findByWorkspaceId(workspaceId: Long): List<RevenueGoal> =
        dsl.select()
            .from(REVENUE_GOALS)
            .where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<RevenueGoal> =
        dsl.select()
            .from(REVENUE_GOALS)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(STATUS.eq(status))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun save(goal: RevenueGoal): RevenueGoal {
        val id = dsl.insertInto(REVENUE_GOALS)
            .set(WORKSPACE_ID, goal.workspaceId)
            .set(NAME, goal.name)
            .set(TARGET_AMOUNT, goal.targetAmount)
            .set(CURRENT_AMOUNT, goal.currentAmount)
            .set(CURRENCY, goal.currency)
            .set(PERIOD, goal.period)
            .set(PLATFORM, goal.platform)
            .set(STATUS, goal.status)
            .set(PROGRESS, goal.progress)
            .set(START_DATE, goal.startDate)
            .set(END_DATE, goal.endDate)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return dsl.select()
            .from(REVENUE_GOALS)
            .where(ID.eq(id))
            .fetchOne()!!
            .toEntity()
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(REVENUE_GOALS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toEntity(): RevenueGoal = RevenueGoal(
        id = get(ID),
        workspaceId = get(WORKSPACE_ID),
        name = get(NAME) ?: "",
        targetAmount = get(TARGET_AMOUNT) ?: 0,
        currentAmount = get(CURRENT_AMOUNT) ?: 0,
        currency = get(CURRENCY) ?: "KRW",
        period = get(PERIOD) ?: "",
        platform = get(PLATFORM) ?: "ALL",
        status = get(STATUS) ?: "ACTIVE",
        progress = get(PROGRESS) ?: 0,
        startDate = localDate(START_DATE) ?: java.time.LocalDate.now(),
        endDate = localDate(END_DATE) ?: java.time.LocalDate.now(),
        createdAt = localDateTime(CREATED_AT) ?: java.time.LocalDateTime.now(),
    )
}

@Repository
class RevenueGoalMilestoneJooqRepository(
    private val dsl: DSLContext,
) : RevenueGoalMilestoneRepository {

    companion object {
        private val GOAL_ID = DSL.field("goal_id", Long::class.java)
        private val LABEL = DSL.field("label", String::class.java)
        private val TARGET_AMOUNT = DSL.field("target_amount", Long::class.java)
        private val REACHED = DSL.field("reached", Boolean::class.java)
        private val REACHED_AT = DSL.field("reached_at", java.time.LocalDateTime::class.java)
    }

    override fun findByGoalId(goalId: Long): List<RevenueGoalMilestone> =
        dsl.select()
            .from(REVENUE_GOAL_MILESTONES)
            .where(GOAL_ID.eq(goalId))
            .orderBy(TARGET_AMOUNT.asc())
            .fetch()
            .map { it.toEntity() }

    private fun Record.toEntity(): RevenueGoalMilestone = RevenueGoalMilestone(
        id = get(ID),
        goalId = get(GOAL_ID),
        label = get(LABEL) ?: "",
        targetAmount = get(TARGET_AMOUNT) ?: 0,
        reached = get(REACHED) ?: false,
        reachedAt = localDateTime(REACHED_AT),
        createdAt = localDateTime(CREATED_AT) ?: java.time.LocalDateTime.now(),
    )
}
