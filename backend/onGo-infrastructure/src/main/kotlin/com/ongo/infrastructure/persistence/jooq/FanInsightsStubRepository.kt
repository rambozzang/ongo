package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.faninsights.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class FanBehaviorJooqRepository(
    private val dsl: DSLContext,
) : FanBehaviorRepository {

    companion object {
        private val TABLE = DSL.table("fan_behaviors")
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val ACTIVE_HOUR = DSL.field("active_hour", Int::class.java)
        private val ACTIVE_DAY = DSL.field("active_day", String::class.java)
        private val WATCH_DURATION = DSL.field("watch_duration", BigDecimal::class.java)
        private val RETURN_RATE = DSL.field("return_rate", BigDecimal::class.java)
        private val COMMENT_RATE = DSL.field("comment_rate", BigDecimal::class.java)
        private val SHARE_RATE = DSL.field("share_rate", BigDecimal::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<FanBehavior> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch { it.toBehavior() }

    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<FanBehavior> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(PLATFORM.eq(platform))
            .fetch { it.toBehavior() }

    private fun Record.toBehavior(): FanBehavior = FanBehavior(
        id = get(ID),
        workspaceId = get(WORKSPACE_ID),
        platform = get(PLATFORM),
        activeHour = get(ACTIVE_HOUR) ?: 0,
        activeDay = get(ACTIVE_DAY),
        watchDuration = get(WATCH_DURATION) ?: BigDecimal.ZERO,
        returnRate = get(RETURN_RATE) ?: BigDecimal.ZERO,
        commentRate = get(COMMENT_RATE) ?: BigDecimal.ZERO,
        shareRate = get(SHARE_RATE) ?: BigDecimal.ZERO,
        updatedAt = localDateTime(UPDATED_AT) ?: java.time.LocalDateTime.now(),
    )
}

@Repository
class FanDemographicJooqRepository(
    private val dsl: DSLContext,
) : FanDemographicRepository {

    companion object {
        private val TABLE = DSL.table("fan_demographics")
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val AGE_GROUP = DSL.field("age_group", String::class.java)
        private val GENDER = DSL.field("gender", String::class.java)
        private val PERCENTAGE = DSL.field("percentage", BigDecimal::class.java)
        private val COUNTRY = DSL.field("country", String::class.java)
        private val CITY = DSL.field("city", String::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<FanDemographic> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch { it.toDemographic() }

    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<FanDemographic> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(PLATFORM.eq(platform))
            .fetch { it.toDemographic() }

    private fun Record.toDemographic(): FanDemographic = FanDemographic(
        id = get(ID),
        workspaceId = get(WORKSPACE_ID),
        platform = get(PLATFORM),
        ageGroup = get(AGE_GROUP),
        gender = get(GENDER),
        percentage = get(PERCENTAGE) ?: BigDecimal.ZERO,
        country = get(COUNTRY),
        city = get(CITY),
        updatedAt = localDateTime(UPDATED_AT) ?: java.time.LocalDateTime.now(),
    )
}

@Repository
class FanSegmentJooqRepository(
    private val dsl: DSLContext,
) : FanSegmentRepository {

    companion object {
        private val TABLE = DSL.table("fan_segments")
        private val NAME = DSL.field("name", String::class.java)
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val MEMBER_COUNT = DSL.field("member_count", Int::class.java)
        private val AVG_ENGAGEMENT = DSL.field("avg_engagement", BigDecimal::class.java)
        private val TOP_INTERESTS = DSL.field("top_interests", String::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<FanSegment> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch { it.toSegment() }

    private fun Record.toSegment(): FanSegment {
        val topInterestsRaw = get("top_interests")
        val topInterestsStr = when (topInterestsRaw) {
            is String -> topInterestsRaw
            else -> topInterestsRaw?.toString() ?: "[]"
        }
        val topInterestsList = topInterestsStr.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }

        return FanSegment(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            name = get(NAME),
            description = get(DESCRIPTION),
            memberCount = get(MEMBER_COUNT) ?: 0,
            avgEngagement = get(AVG_ENGAGEMENT) ?: BigDecimal.ZERO,
            topInterests = topInterestsList,
            platform = get(PLATFORM),
            createdAt = localDateTime(CREATED_AT) ?: java.time.LocalDateTime.now(),
        )
    }
}
