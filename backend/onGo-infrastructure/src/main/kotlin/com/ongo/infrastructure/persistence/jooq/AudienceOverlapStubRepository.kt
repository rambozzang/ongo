package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.audienceoverlap.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class AudienceOverlapResultJooqRepository(
    private val dsl: DSLContext,
) : AudienceOverlapResultRepository {

    companion object {
        private val TABLE = DSL.table("audience_overlap_results")
        private val PLATFORM_A = DSL.field("platform_a", String::class.java)
        private val PLATFORM_B = DSL.field("platform_b", String::class.java)
        private val OVERLAP_PERCENT = DSL.field("overlap_percent", BigDecimal::class.java)
        private val UNIQUE_TO_A = DSL.field("unique_to_a", Long::class.java)
        private val UNIQUE_TO_B = DSL.field("unique_to_b", Long::class.java)
        private val SHARED_AUDIENCE = DSL.field("shared_audience", Long::class.java)
        private val ANALYZED_AT = DSL.field("analyzed_at", java.time.LocalDateTime::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<AudienceOverlapResult> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch { it.toResult() }

    override fun save(result: AudienceOverlapResult): AudienceOverlapResult {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, result.workspaceId)
            .set(PLATFORM_A, result.platformA)
            .set(PLATFORM_B, result.platformB)
            .set(OVERLAP_PERCENT, result.overlapPercent)
            .set(UNIQUE_TO_A, result.uniqueToA)
            .set(UNIQUE_TO_B, result.uniqueToB)
            .set(SHARED_AUDIENCE, result.sharedAudience)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toResult()
    }

    private fun Record.toResult(): AudienceOverlapResult = AudienceOverlapResult(
        id = get(ID),
        workspaceId = get(WORKSPACE_ID),
        platformA = get(PLATFORM_A),
        platformB = get(PLATFORM_B),
        overlapPercent = get(OVERLAP_PERCENT) ?: BigDecimal.ZERO,
        uniqueToA = get(UNIQUE_TO_A) ?: 0,
        uniqueToB = get(UNIQUE_TO_B) ?: 0,
        sharedAudience = get(SHARED_AUDIENCE) ?: 0,
        analyzedAt = localDateTime(ANALYZED_AT) ?: java.time.LocalDateTime.now(),
    )
}

@Repository
class OverlapSegmentJooqRepository(
    private val dsl: DSLContext,
) : OverlapSegmentRepository {

    companion object {
        private val TABLE = DSL.table("overlap_segments")
        private val NAME = DSL.field("name", String::class.java)
        private val PLATFORMS = DSL.field("platforms", String::class.java)
        private val AUDIENCE_SIZE = DSL.field("audience_size", Long::class.java)
        private val ENGAGEMENT_RATE = DSL.field("engagement_rate", BigDecimal::class.java)
        private val TOP_INTEREST = DSL.field("top_interest", String::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<OverlapSegment> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch { it.toSegment() }

    private fun Record.toSegment(): OverlapSegment {
        val platformsRaw = get("platforms")
        val platformsStr = when (platformsRaw) {
            is String -> platformsRaw
            else -> platformsRaw?.toString() ?: "[]"
        }
        val platformsList = platformsStr.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }

        return OverlapSegment(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            name = get(NAME),
            platforms = platformsList,
            audienceSize = get(AUDIENCE_SIZE) ?: 0,
            engagementRate = get(ENGAGEMENT_RATE) ?: BigDecimal.ZERO,
            topInterest = get(TOP_INTEREST),
            createdAt = localDateTime(CREATED_AT) ?: java.time.LocalDateTime.now(),
        )
    }
}
