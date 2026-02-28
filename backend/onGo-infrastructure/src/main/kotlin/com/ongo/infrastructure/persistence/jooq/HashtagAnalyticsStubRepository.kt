package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.hashtaganalytics.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class HashtagPerformanceJooqRepository(
    private val dsl: DSLContext,
) : HashtagPerformanceRepository {

    companion object {
        private val TABLE = DSL.table("hashtag_performances")
        private val HASHTAG = DSL.field("hashtag", String::class.java)
        private val USAGE_COUNT = DSL.field("usage_count", Int::class.java)
        private val TOTAL_VIEWS = DSL.field("total_views", Long::class.java)
        private val AVG_ENGAGEMENT = DSL.field("avg_engagement", BigDecimal::class.java)
        private val GROWTH_RATE = DSL.field("growth_rate", BigDecimal::class.java)
        private val TREND_DIRECTION = DSL.field("trend_direction", String::class.java)
        private val CATEGORY = DSL.field("category", String::class.java)
        private val LAST_USED_AT = DSL.field("last_used_at", LocalDateTime::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<HashtagPerformance> =
        dsl.select().from(TABLE).where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(TOTAL_VIEWS.desc())
            .fetch().map { it.toPerformance() }

    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<HashtagPerformance> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(PLATFORM.eq(platform))
            .orderBy(TOTAL_VIEWS.desc())
            .fetch().map { it.toPerformance() }

    override fun findById(id: Long): HashtagPerformance? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toPerformance()

    override fun save(performance: HashtagPerformance): HashtagPerformance {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, performance.workspaceId)
            .set(HASHTAG, performance.hashtag)
            .set(PLATFORM, performance.platform)
            .set(USAGE_COUNT, performance.usageCount)
            .set(TOTAL_VIEWS, performance.totalViews)
            .set(AVG_ENGAGEMENT, performance.avgEngagement)
            .set(GROWTH_RATE, performance.growthRate)
            .set(TREND_DIRECTION, performance.trendDirection)
            .set(CATEGORY, performance.category)
            .set(LAST_USED_AT, performance.lastUsedAt)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun update(performance: HashtagPerformance): HashtagPerformance {
        dsl.update(TABLE)
            .set(HASHTAG, performance.hashtag)
            .set(PLATFORM, performance.platform)
            .set(USAGE_COUNT, performance.usageCount)
            .set(TOTAL_VIEWS, performance.totalViews)
            .set(AVG_ENGAGEMENT, performance.avgEngagement)
            .set(GROWTH_RATE, performance.growthRate)
            .set(TREND_DIRECTION, performance.trendDirection)
            .set(CATEGORY, performance.category)
            .set(LAST_USED_AT, performance.lastUsedAt)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(performance.id))
            .execute()

        return findById(performance.id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toPerformance(): HashtagPerformance =
        HashtagPerformance(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            hashtag = get(HASHTAG),
            platform = get(PLATFORM),
            usageCount = get(USAGE_COUNT) ?: 0,
            totalViews = get(TOTAL_VIEWS) ?: 0,
            avgEngagement = get(AVG_ENGAGEMENT) ?: BigDecimal.ZERO,
            growthRate = get(GROWTH_RATE) ?: BigDecimal.ZERO,
            trendDirection = get(TREND_DIRECTION) ?: "STABLE",
            category = get(CATEGORY),
            lastUsedAt = localDateTime(LAST_USED_AT),
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
            updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
        )
}

@Repository
class HashtagGroupJooqRepository(
    private val dsl: DSLContext,
) : HashtagGroupRepository {

    companion object {
        private val TABLE = DSL.table("hashtag_groups")
        private val NAME = DSL.field("name", String::class.java)
        private val HASHTAGS = DSL.field("hashtags", String::class.java)
        private val USAGE_COUNT = DSL.field("usage_count", Int::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<HashtagGroup> =
        dsl.select().from(TABLE).where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(Fields.CREATED_AT.desc())
            .fetch().map { it.toGroup() }

    override fun findById(id: Long): HashtagGroup? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toGroup()

    override fun save(group: HashtagGroup): HashtagGroup {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, group.workspaceId)
            .set(NAME, group.name)
            .set(HASHTAGS, DSL.field("?::jsonb", String::class.java, com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(group.hashtags)))
            .set(PLATFORM, group.platform)
            .set(USAGE_COUNT, group.usageCount)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Record.toGroup(): HashtagGroup {
        val hashtagsRaw = get("hashtags")
        val hashtagsList: List<String> = when (hashtagsRaw) {
            is String -> try {
                com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readValue(hashtagsRaw, List::class.java) as List<String>
            } catch (_: Exception) {
                emptyList()
            }
            else -> try {
                com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readValue(hashtagsRaw?.toString() ?: "[]", List::class.java) as List<String>
            } catch (_: Exception) {
                emptyList()
            }
        }
        return HashtagGroup(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            name = get(NAME),
            hashtags = hashtagsList,
            platform = get(PLATFORM),
            usageCount = get(USAGE_COUNT) ?: 0,
            createdAt = localDateTime(Fields.CREATED_AT) ?: LocalDateTime.now(),
            updatedAt = localDateTime(Fields.UPDATED_AT) ?: LocalDateTime.now(),
        )
    }
}
