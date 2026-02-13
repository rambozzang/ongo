package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.goal.Goal
import com.ongo.domain.goal.GoalMilestone
import com.ongo.domain.goal.GoalRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CURRENT_VALUE
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.END_DATE
import com.ongo.infrastructure.persistence.jooq.Fields.GOAL_ID
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IS_REACHED
import com.ongo.infrastructure.persistence.jooq.Fields.METRIC_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.REACHED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.START_DATE
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TARGET_VALUE
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.GOALS
import com.ongo.infrastructure.persistence.jooq.Tables.GOAL_MILESTONES
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class GoalJooqRepository(
    private val dsl: DSLContext,
) : GoalRepository {

    override fun findById(id: Long): Goal? =
        dsl.select()
            .from(GOALS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toGoal()

    override fun findByUserId(userId: Long): List<Goal> =
        dsl.select()
            .from(GOALS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toGoal() }

    override fun save(goal: Goal): Goal {
        val record = dsl.insertInto(GOALS)
            .set(USER_ID, goal.userId)
            .set(TITLE, goal.title)
            .set(DESCRIPTION, goal.description)
            .set(METRIC_TYPE, goal.metricType)
            .set(TARGET_VALUE, goal.targetValue)
            .set(CURRENT_VALUE, goal.currentValue)
            .set(START_DATE, goal.startDate)
            .set(END_DATE, goal.endDate)
            .set(STATUS, goal.status)
            .returning()
            .fetchOne()!!

        return record.toGoal()
    }

    override fun update(goal: Goal): Goal {
        val record = dsl.update(GOALS)
            .set(TITLE, goal.title)
            .set(DESCRIPTION, goal.description)
            .set(METRIC_TYPE, goal.metricType)
            .set(TARGET_VALUE, goal.targetValue)
            .set(CURRENT_VALUE, goal.currentValue)
            .set(START_DATE, goal.startDate)
            .set(END_DATE, goal.endDate)
            .set(STATUS, goal.status)
            .set(UPDATED_AT, java.time.LocalDateTime.now())
            .where(ID.eq(goal.id))
            .returning()
            .fetchOne()!!

        return record.toGoal()
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(GOALS)
            .where(ID.eq(id))
            .execute()
    }

    override fun findMilestonesByGoalId(goalId: Long): List<GoalMilestone> =
        dsl.select()
            .from(GOAL_MILESTONES)
            .where(GOAL_ID.eq(goalId))
            .orderBy(TARGET_VALUE.asc())
            .fetch()
            .map { it.toMilestone() }

    override fun saveMilestone(milestone: GoalMilestone): GoalMilestone {
        val record = dsl.insertInto(GOAL_MILESTONES)
            .set(GOAL_ID, milestone.goalId)
            .set(TITLE, milestone.title)
            .set(TARGET_VALUE, milestone.targetValue)
            .set(IS_REACHED, milestone.isReached)
            .set(REACHED_AT, milestone.reachedAt)
            .returning()
            .fetchOne()!!

        return record.toMilestone()
    }

    override fun deleteMilestone(id: Long) {
        dsl.deleteFrom(GOAL_MILESTONES)
            .where(ID.eq(id))
            .execute()
    }

    override fun updateMilestone(milestone: GoalMilestone): GoalMilestone {
        val record = dsl.update(GOAL_MILESTONES)
            .set(TITLE, milestone.title)
            .set(TARGET_VALUE, milestone.targetValue)
            .set(IS_REACHED, milestone.isReached)
            .set(REACHED_AT, milestone.reachedAt)
            .where(ID.eq(milestone.id))
            .returning()
            .fetchOne()!!

        return record.toMilestone()
    }

    private fun Record.toGoal(): Goal = Goal(
        id = get(ID),
        userId = get(USER_ID),
        title = get(TITLE),
        description = get(DESCRIPTION),
        metricType = get(METRIC_TYPE),
        targetValue = get(TARGET_VALUE),
        currentValue = get(CURRENT_VALUE) ?: 0,
        startDate = localDate(START_DATE)!!,
        endDate = localDate(END_DATE)!!,
        status = get(STATUS),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )

    private fun Record.toMilestone(): GoalMilestone = GoalMilestone(
        id = get(ID),
        goalId = get(GOAL_ID),
        title = get(TITLE),
        targetValue = get(TARGET_VALUE),
        isReached = get(IS_REACHED) ?: false,
        reachedAt = localDateTime(REACHED_AT),
        createdAt = localDateTime(CREATED_AT),
    )
}
