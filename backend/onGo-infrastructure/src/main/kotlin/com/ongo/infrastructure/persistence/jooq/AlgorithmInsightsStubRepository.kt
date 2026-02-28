package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.algorithminsights.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class AlgorithmChangeJooqRepository(
    private val dsl: DSLContext,
) : AlgorithmChangeRepository {

    companion object {
        private val TABLE = DSL.table("algorithm_changes")
        private val IMPACT = DSL.field("impact", String::class.java)
        private val AFFECTED_AREAS = DSL.field("affected_areas", String::class.java)
        private val DETECTED_AT = DSL.field("detected_at", LocalDateTime::class.java)
        private val RECOMMENDATION = DSL.field("recommendation", String::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<AlgorithmChange> =
        dsl.select().from(TABLE).where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(DETECTED_AT.desc())
            .fetch().map { it.toChange() }

    override fun findById(id: Long): AlgorithmChange? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toChange()

    override fun save(change: AlgorithmChange): AlgorithmChange {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, change.workspaceId)
            .set(PLATFORM, change.platform)
            .set(TITLE, change.title)
            .set(DESCRIPTION, change.description)
            .set(IMPACT, change.impact)
            .set(AFFECTED_AREAS, DSL.field("?::jsonb", String::class.java,
                com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(change.affectedAreas)))
            .set(RECOMMENDATION, change.recommendation)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    @Suppress("UNCHECKED_CAST")
    private fun Record.toChange(): AlgorithmChange {
        val areasRaw = get("affected_areas")
        val areasList: List<String> = when (areasRaw) {
            is String -> try {
                com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readValue(areasRaw, List::class.java) as List<String>
            } catch (_: Exception) {
                emptyList()
            }
            else -> try {
                com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readValue(areasRaw?.toString() ?: "[]", List::class.java) as List<String>
            } catch (_: Exception) {
                emptyList()
            }
        }
        return AlgorithmChange(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            platform = get(PLATFORM),
            title = get(TITLE),
            description = get(DESCRIPTION),
            impact = get(IMPACT) ?: "MEDIUM",
            affectedAreas = areasList,
            detectedAt = localDateTime(DETECTED_AT) ?: LocalDateTime.now(),
            recommendation = get(RECOMMENDATION),
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
        )
    }
}

@Repository
class AlgorithmInsightJooqRepository(
    private val dsl: DSLContext,
) : AlgorithmInsightRepository {

    companion object {
        private val TABLE = DSL.table("algorithm_insights")
        private val FACTOR = DSL.field("factor", String::class.java)
        private val IMPORTANCE = DSL.field("importance", Int::class.java)
        private val CURRENT_SCORE = DSL.field("current_score", Int::class.java)
        private val RECOMMENDATION = DSL.field("recommendation", String::class.java)
        private val CATEGORY = DSL.field("category", String::class.java)
        private val TREND = DSL.field("trend", String::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<AlgorithmInsight> =
        dsl.select().from(TABLE).where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(IMPORTANCE.desc())
            .fetch().map { it.toInsight() }

    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<AlgorithmInsight> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(PLATFORM.eq(platform))
            .orderBy(IMPORTANCE.desc())
            .fetch().map { it.toInsight() }

    override fun findById(id: Long): AlgorithmInsight? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toInsight()

    override fun save(insight: AlgorithmInsight): AlgorithmInsight {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, insight.workspaceId)
            .set(PLATFORM, insight.platform)
            .set(FACTOR, insight.factor)
            .set(IMPORTANCE, insight.importance)
            .set(CURRENT_SCORE, insight.currentScore)
            .set(RECOMMENDATION, insight.recommendation)
            .set(CATEGORY, insight.category)
            .set(TREND, insight.trend)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    private fun Record.toInsight(): AlgorithmInsight =
        AlgorithmInsight(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            platform = get(PLATFORM),
            factor = get(FACTOR),
            importance = get(IMPORTANCE) ?: 0,
            currentScore = get(CURRENT_SCORE) ?: 0,
            recommendation = get(RECOMMENDATION),
            category = get(CATEGORY),
            trend = get(TREND) ?: "STABLE",
            updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
        )
}

@Repository
class AlgorithmScoreJooqRepository(
    private val dsl: DSLContext,
) : AlgorithmScoreRepository {

    companion object {
        private val TABLE = DSL.table("algorithm_scores")
        private val OVERALL_SCORE = DSL.field("overall_score", Int::class.java)
        private val CONTENT_SCORE = DSL.field("content_score", Int::class.java)
        private val ENGAGEMENT_SCORE = DSL.field("engagement_score", Int::class.java)
        private val METADATA_SCORE = DSL.field("metadata_score", Int::class.java)
        private val CONSISTENCY_SCORE = DSL.field("consistency_score", Int::class.java)
        private val AUDIENCE_SCORE = DSL.field("audience_score", Int::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<AlgorithmScore> =
        dsl.select().from(TABLE).where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(UPDATED_AT.desc())
            .fetch().map { it.toScore() }

    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): AlgorithmScore? =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(PLATFORM.eq(platform))
            .fetchOne()?.toScore()

    override fun save(score: AlgorithmScore): AlgorithmScore {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, score.workspaceId)
            .set(PLATFORM, score.platform)
            .set(OVERALL_SCORE, score.overallScore)
            .set(CONTENT_SCORE, score.contentScore)
            .set(ENGAGEMENT_SCORE, score.engagementScore)
            .set(METADATA_SCORE, score.metadataScore)
            .set(CONSISTENCY_SCORE, score.consistencyScore)
            .set(AUDIENCE_SCORE, score.audienceScore)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toScore()
    }

    override fun update(score: AlgorithmScore): AlgorithmScore {
        dsl.update(TABLE)
            .set(OVERALL_SCORE, score.overallScore)
            .set(CONTENT_SCORE, score.contentScore)
            .set(ENGAGEMENT_SCORE, score.engagementScore)
            .set(METADATA_SCORE, score.metadataScore)
            .set(CONSISTENCY_SCORE, score.consistencyScore)
            .set(AUDIENCE_SCORE, score.audienceScore)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(score.id))
            .execute()

        return dsl.select().from(TABLE).where(ID.eq(score.id)).fetchOne()!!.toScore()
    }

    private fun Record.toScore(): AlgorithmScore =
        AlgorithmScore(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            platform = get(PLATFORM),
            overallScore = get(OVERALL_SCORE) ?: 0,
            contentScore = get(CONTENT_SCORE) ?: 0,
            engagementScore = get(ENGAGEMENT_SCORE) ?: 0,
            metadataScore = get(METADATA_SCORE) ?: 0,
            consistencyScore = get(CONSISTENCY_SCORE) ?: 0,
            audienceScore = get(AUDIENCE_SCORE) ?: 0,
            updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
        )
}
