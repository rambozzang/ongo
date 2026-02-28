package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.growthcoach.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class GrowthCoachJooqRepository(
    private val dsl: DSLContext,
) : GrowthCoachRepository {

    companion object {
        // growth_goals
        private val GOAL_TABLE = DSL.table("growth_goals")
        private val TYPE = DSL.field("type", String::class.java)
        private val TARGET_VALUE = DSL.field("target_value", Long::class.java)
        private val CURRENT_VALUE = DSL.field("current_value", Long::class.java)
        private val DEADLINE = DSL.field("deadline", String::class.java)
        private val PROGRESS = DSL.field("progress", Int::class.java)

        // weekly_reports
        private val REPORT_TABLE = DSL.table("weekly_reports")
        private val WEEK_START = DSL.field("week_start", String::class.java)
        private val WEEK_END = DSL.field("week_end", String::class.java)
        private val SUMMARY = DSL.field("summary", String::class.java)
        private val HIGHLIGHTS = DSL.field("highlights", String::class.java)
        private val CONCERNS = DSL.field("concerns", String::class.java)
        private val SUBSCRIBER_GROWTH = DSL.field("subscriber_growth", Int::class.java)
        private val VIEWS_GROWTH = DSL.field("views_growth", Int::class.java)
        private val ENGAGEMENT_CHANGE = DSL.field("engagement_change", Double::class.java)
        private val TOP_CONTENT = DSL.field("top_content", String::class.java)
        private val ACTION_ITEMS = DSL.field("action_items", String::class.java)
        private val OVERALL_SCORE = DSL.field("overall_score", Int::class.java)
        private val GENERATED_AT = DSL.field("generated_at", LocalDateTime::class.java)
    }

    // ==================== GrowthGoal ====================

    override fun findGoalById(id: Long): GrowthGoal? =
        dsl.select().from(GOAL_TABLE)
            .where(ID.eq(id))
            .fetchOne()?.toGoal()

    override fun findGoalsByUserId(userId: Long): List<GrowthGoal> =
        dsl.select().from(GOAL_TABLE)
            .where(USER_ID.eq(userId))
            .fetch { it.toGoal() }

    override fun saveGoal(goal: GrowthGoal): GrowthGoal {
        val id = dsl.insertInto(GOAL_TABLE)
            .set(USER_ID, goal.userId)
            .set(TYPE, goal.type)
            .set(TARGET_VALUE, goal.targetValue)
            .set(CURRENT_VALUE, goal.currentValue)
            .set(DEADLINE, goal.deadline)
            .set(PROGRESS, goal.progress)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findGoalById(id)!!
    }

    override fun updateGoal(goal: GrowthGoal): GrowthGoal {
        dsl.update(GOAL_TABLE)
            .set(TYPE, goal.type)
            .set(TARGET_VALUE, goal.targetValue)
            .set(CURRENT_VALUE, goal.currentValue)
            .set(DEADLINE, goal.deadline)
            .set(PROGRESS, goal.progress)
            .where(ID.eq(goal.id))
            .execute()

        return findGoalById(goal.id!!)!!
    }

    override fun deleteGoal(id: Long) {
        dsl.deleteFrom(GOAL_TABLE).where(ID.eq(id)).execute()
    }

    // ==================== WeeklyReport ====================

    override fun findReportById(id: Long): WeeklyReport? =
        dsl.select().from(REPORT_TABLE)
            .where(ID.eq(id))
            .fetchOne()?.toReport()

    override fun findReportsByUserId(userId: Long): List<WeeklyReport> =
        dsl.select().from(REPORT_TABLE)
            .where(USER_ID.eq(userId))
            .fetch { it.toReport() }

    override fun saveReport(report: WeeklyReport): WeeklyReport {
        val id = dsl.insertInto(REPORT_TABLE)
            .set(USER_ID, report.userId)
            .set(WEEK_START, report.weekStart)
            .set(WEEK_END, report.weekEnd)
            .set(SUMMARY, report.summary)
            .set(HIGHLIGHTS, DSL.field("?::jsonb", String::class.java, report.highlights))
            .set(CONCERNS, DSL.field("?::jsonb", String::class.java, report.concerns))
            .set(SUBSCRIBER_GROWTH, report.subscriberGrowth)
            .set(VIEWS_GROWTH, report.viewsGrowth)
            .set(ENGAGEMENT_CHANGE, report.engagementChange)
            .set(TOP_CONTENT, DSL.field("?::jsonb", String::class.java, report.topContent))
            .set(ACTION_ITEMS, DSL.field("?::jsonb", String::class.java, report.actionItems))
            .set(OVERALL_SCORE, report.overallScore)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findReportById(id)!!
    }

    override fun updateReport(report: WeeklyReport): WeeklyReport {
        dsl.update(REPORT_TABLE)
            .set(WEEK_START, report.weekStart)
            .set(WEEK_END, report.weekEnd)
            .set(SUMMARY, report.summary)
            .set(HIGHLIGHTS, DSL.field("?::jsonb", String::class.java, report.highlights))
            .set(CONCERNS, DSL.field("?::jsonb", String::class.java, report.concerns))
            .set(SUBSCRIBER_GROWTH, report.subscriberGrowth)
            .set(VIEWS_GROWTH, report.viewsGrowth)
            .set(ENGAGEMENT_CHANGE, report.engagementChange)
            .set(TOP_CONTENT, DSL.field("?::jsonb", String::class.java, report.topContent))
            .set(ACTION_ITEMS, DSL.field("?::jsonb", String::class.java, report.actionItems))
            .set(OVERALL_SCORE, report.overallScore)
            .where(ID.eq(report.id))
            .execute()

        return findReportById(report.id!!)!!
    }

    override fun deleteReport(id: Long) {
        dsl.deleteFrom(REPORT_TABLE).where(ID.eq(id)).execute()
    }

    // ==================== Record mappers ====================

    private fun Record.toGoal(): GrowthGoal =
        GrowthGoal(
            id = get(ID),
            userId = get(USER_ID),
            type = get(TYPE),
            targetValue = get(TARGET_VALUE) ?: 0,
            currentValue = get(CURRENT_VALUE) ?: 0,
            deadline = get(DEADLINE) ?: "",
            progress = get(PROGRESS) ?: 0,
            createdAt = localDateTime(CREATED_AT),
        )

    private fun Record.toReport(): WeeklyReport {
        val highlightsRaw = get("highlights")
        val concernsRaw = get("concerns")
        val topContentRaw = get("top_content")
        val actionItemsRaw = get("action_items")
        return WeeklyReport(
            id = get(ID),
            userId = get(USER_ID),
            weekStart = get(WEEK_START),
            weekEnd = get(WEEK_END),
            summary = get(SUMMARY) ?: "",
            highlights = when (highlightsRaw) {
                is String -> highlightsRaw
                else -> highlightsRaw?.toString() ?: "[]"
            },
            concerns = when (concernsRaw) {
                is String -> concernsRaw
                else -> concernsRaw?.toString() ?: "[]"
            },
            subscriberGrowth = get(SUBSCRIBER_GROWTH) ?: 0,
            viewsGrowth = get(VIEWS_GROWTH) ?: 0,
            engagementChange = get(ENGAGEMENT_CHANGE) ?: 0.0,
            topContent = when (topContentRaw) {
                is String -> topContentRaw
                else -> topContentRaw?.toString() ?: "[]"
            },
            actionItems = when (actionItemsRaw) {
                is String -> actionItemsRaw
                else -> actionItemsRaw?.toString() ?: "[]"
            },
            overallScore = get(OVERALL_SCORE) ?: 0,
            generatedAt = localDateTime(GENERATED_AT),
        )
    }
}
