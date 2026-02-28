package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.teamperformance.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class TeamActivityJooqRepository(
    private val dsl: DSLContext,
) : TeamActivityRepository {

    companion object {
        private val TABLE = DSL.table("team_activities")
        private val MEMBER_ID = DSL.field("member_id", Long::class.java)
        private val MEMBER_NAME = DSL.field("member_name", String::class.java)
        private val ACTION = DSL.field("action", String::class.java)
        private val TARGET = DSL.field("target", String::class.java)
        private val TIMESTAMP = DSL.field("timestamp", LocalDateTime::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long, limit: Int): List<TeamActivity> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(TIMESTAMP.desc())
            .limit(limit)
            .fetch().map { it.toTeamActivity() }

    override fun findByMemberId(memberId: Long): List<TeamActivity> =
        dsl.select().from(TABLE)
            .where(MEMBER_ID.eq(memberId))
            .orderBy(TIMESTAMP.desc())
            .fetch().map { it.toTeamActivity() }

    override fun save(activity: TeamActivity): TeamActivity {
        val newId = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, activity.workspaceId)
            .set(MEMBER_ID, activity.memberId)
            .set(MEMBER_NAME, activity.memberName)
            .set(ACTION, activity.action)
            .set(TARGET, activity.target)
            .set(TIMESTAMP, activity.timestamp)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return dsl.select().from(TABLE).where(ID.eq(newId)).fetchOne()!!.toTeamActivity()
    }

    private fun Record.toTeamActivity(): TeamActivity =
        TeamActivity(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            memberId = get(MEMBER_ID),
            memberName = get(MEMBER_NAME),
            action = get(ACTION),
            target = get(TARGET),
            timestamp = localDateTime(TIMESTAMP) ?: LocalDateTime.now(),
        )
}

@Repository
class TeamMemberPerformanceJooqRepository(
    private val dsl: DSLContext,
) : TeamMemberPerformanceRepository {

    companion object {
        private val TABLE = DSL.table("team_member_performances")
        private val NAME = DSL.field("name", String::class.java)
        private val EMAIL = DSL.field("email", String::class.java)
        private val ROLE = DSL.field("role", String::class.java)
        private val AVATAR = DSL.field("avatar", String::class.java)
        private val TASKS_COMPLETED = DSL.field("tasks_completed", Int::class.java)
        private val TASKS_ASSIGNED = DSL.field("tasks_assigned", Int::class.java)
        private val COMPLETION_RATE = DSL.field("completion_rate", BigDecimal::class.java)
        private val AVG_COMPLETION_TIME = DSL.field("avg_completion_time", BigDecimal::class.java)
        private val CONTENT_PRODUCED = DSL.field("content_produced", Int::class.java)
        private val ON_TIME_RATE = DSL.field("on_time_rate", BigDecimal::class.java)
        private val RATING = DSL.field("rating", BigDecimal::class.java)
        private val STREAK = DSL.field("streak", Int::class.java)
        private val LAST_ACTIVE_AT = DSL.field("last_active_at", LocalDateTime::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<TeamMemberPerformance> =
        dsl.select().from(TABLE).where(WORKSPACE_ID.eq(workspaceId)).fetch().map { it.toTeamMemberPerformance() }

    override fun findById(id: Long): TeamMemberPerformance? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toTeamMemberPerformance()

    override fun save(member: TeamMemberPerformance): TeamMemberPerformance {
        val newId = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, member.workspaceId)
            .set(NAME, member.name)
            .set(EMAIL, member.email)
            .set(ROLE, member.role)
            .set(AVATAR, member.avatar)
            .set(TASKS_COMPLETED, member.tasksCompleted)
            .set(TASKS_ASSIGNED, member.tasksAssigned)
            .set(COMPLETION_RATE, member.completionRate)
            .set(AVG_COMPLETION_TIME, member.avgCompletionTime)
            .set(CONTENT_PRODUCED, member.contentProduced)
            .set(ON_TIME_RATE, member.onTimeRate)
            .set(RATING, member.rating)
            .set(STREAK, member.streak)
            .set(LAST_ACTIVE_AT, member.lastActiveAt)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(newId)!!
    }

    override fun update(member: TeamMemberPerformance): TeamMemberPerformance {
        dsl.update(TABLE)
            .set(NAME, member.name)
            .set(EMAIL, member.email)
            .set(ROLE, member.role)
            .set(AVATAR, member.avatar)
            .set(TASKS_COMPLETED, member.tasksCompleted)
            .set(TASKS_ASSIGNED, member.tasksAssigned)
            .set(COMPLETION_RATE, member.completionRate)
            .set(AVG_COMPLETION_TIME, member.avgCompletionTime)
            .set(CONTENT_PRODUCED, member.contentProduced)
            .set(ON_TIME_RATE, member.onTimeRate)
            .set(RATING, member.rating)
            .set(STREAK, member.streak)
            .set(LAST_ACTIVE_AT, member.lastActiveAt)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(member.id))
            .execute()
        return findById(member.id)!!
    }

    private fun Record.toTeamMemberPerformance(): TeamMemberPerformance =
        TeamMemberPerformance(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            name = get(NAME),
            email = get(EMAIL),
            role = get(ROLE),
            avatar = get(AVATAR),
            tasksCompleted = get(TASKS_COMPLETED) ?: 0,
            tasksAssigned = get(TASKS_ASSIGNED) ?: 0,
            completionRate = get(COMPLETION_RATE) ?: BigDecimal.ZERO,
            avgCompletionTime = get(AVG_COMPLETION_TIME) ?: BigDecimal.ZERO,
            contentProduced = get(CONTENT_PRODUCED) ?: 0,
            onTimeRate = get(ON_TIME_RATE) ?: BigDecimal.ZERO,
            rating = get(RATING) ?: BigDecimal.ZERO,
            streak = get(STREAK) ?: 0,
            lastActiveAt = localDateTime(LAST_ACTIVE_AT),
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
            updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
        )
}
