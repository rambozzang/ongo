package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.platformhealth.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class PlatformHealthScoreJooqRepository(
    private val dsl: DSLContext,
) : PlatformHealthScoreRepository {

    companion object {
        private val TABLE = DSL.table("platform_health_scores")
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val OVERALL_SCORE = DSL.field("overall_score", Int::class.java)
        private val GROWTH_SCORE = DSL.field("growth_score", Int::class.java)
        private val ENGAGEMENT_SCORE = DSL.field("engagement_score", Int::class.java)
        private val CONSISTENCY_SCORE = DSL.field("consistency_score", Int::class.java)
        private val AUDIENCE_SCORE = DSL.field("audience_score", Int::class.java)
        private val TREND = DSL.field("trend", String::class.java)
        private val CHECKED_AT = DSL.field("checked_at", java.time.LocalDateTime::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<PlatformHealthScore> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch { it.toHealthScore() }

    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): PlatformHealthScore? =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(PLATFORM.eq(platform))
            .fetchOne()?.toHealthScore()

    private fun Record.toHealthScore(): PlatformHealthScore = PlatformHealthScore(
        id = get(ID),
        workspaceId = get(WORKSPACE_ID),
        platform = get(PLATFORM),
        overallScore = get(OVERALL_SCORE) ?: 0,
        growthScore = get(GROWTH_SCORE) ?: 0,
        engagementScore = get(ENGAGEMENT_SCORE) ?: 0,
        consistencyScore = get(CONSISTENCY_SCORE) ?: 0,
        audienceScore = get(AUDIENCE_SCORE) ?: 0,
        trend = get(TREND) ?: "STABLE",
        checkedAt = localDateTime(CHECKED_AT) ?: java.time.LocalDateTime.now(),
    )
}

@Repository
class HealthIssueJooqRepository(
    private val dsl: DSLContext,
) : HealthIssueRepository {

    companion object {
        private val TABLE = DSL.table("health_issues")
        private val HEALTH_SCORE_ID = DSL.field("health_score_id", Long::class.java)
        private val SEVERITY = DSL.field("severity", String::class.java)
        private val CATEGORY = DSL.field("category", String::class.java)
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val RECOMMENDATION = DSL.field("recommendation", String::class.java)
    }

    override fun findByHealthScoreId(healthScoreId: Long): List<HealthIssue> =
        dsl.select().from(TABLE)
            .where(HEALTH_SCORE_ID.eq(healthScoreId))
            .fetch { it.toHealthIssue() }

    private fun Record.toHealthIssue(): HealthIssue = HealthIssue(
        id = get(ID),
        healthScoreId = get(HEALTH_SCORE_ID),
        severity = get(SEVERITY) ?: "LOW",
        category = get(CATEGORY),
        description = get(DESCRIPTION),
        recommendation = get(RECOMMENDATION),
        createdAt = localDateTime(CREATED_AT) ?: java.time.LocalDateTime.now(),
    )
}
