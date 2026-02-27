package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.audiencesegment.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.AUDIENCE_SEGMENTS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class AudienceSegmentJooqRepository(
    private val dsl: DSLContext,
) : AudienceSegmentRepository {

    companion object {
        private val CRITERIA = DSL.field("criteria", String::class.java)
        private val SIZE = DSL.field("size", Long::class.java)
        private val PERCENTAGE = DSL.field("percentage", Double::class.java)
        private val AVG_WATCH_TIME = DSL.field("avg_watch_time", Double::class.java)
        private val AVG_RETENTION = DSL.field("avg_retention", Double::class.java)
        private val AVG_CTR = DSL.field("avg_ctr", Double::class.java)
        private val AVG_ENGAGEMENT = DSL.field("avg_engagement", Double::class.java)
        private val GROWTH_RATE = DSL.field("growth_rate", Double::class.java)
        private val REVENUE_CONTRIBUTION = DSL.field("revenue_contribution", Double::class.java)
        private val TOP_CONTENT_CATEGORIES = DSL.field("top_content_categories", String::class.java)
        private val BEST_POSTING_TIMES = DSL.field("best_posting_times", String::class.java)
    }

    override fun findById(id: Long): AudienceSegment? =
        dsl.select()
            .from(AUDIENCE_SEGMENTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toEntity()

    override fun findByUserId(userId: Long): List<AudienceSegment> =
        dsl.select()
            .from(AUDIENCE_SEGMENTS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun save(segment: AudienceSegment): AudienceSegment {
        val id = dsl.insertInto(AUDIENCE_SEGMENTS)
            .set(USER_ID, segment.userId)
            .set(NAME, segment.name)
            .set(TYPE, segment.type)
            .set(CRITERIA, segment.criteria)
            .set(SIZE, segment.size)
            .set(PERCENTAGE, segment.percentage)
            .set(AVG_WATCH_TIME, segment.avgWatchTime)
            .set(AVG_RETENTION, segment.avgRetention)
            .set(AVG_CTR, segment.avgCtr)
            .set(AVG_ENGAGEMENT, segment.avgEngagement)
            .set(GROWTH_RATE, segment.growthRate)
            .set(REVENUE_CONTRIBUTION, segment.revenueContribution)
            .set(TOP_CONTENT_CATEGORIES, segment.topContentCategories)
            .set(BEST_POSTING_TIMES, segment.bestPostingTimes)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(segment: AudienceSegment): AudienceSegment {
        dsl.update(AUDIENCE_SEGMENTS)
            .set(NAME, segment.name)
            .set(TYPE, segment.type)
            .set(CRITERIA, segment.criteria)
            .set(SIZE, segment.size)
            .set(PERCENTAGE, segment.percentage)
            .set(AVG_WATCH_TIME, segment.avgWatchTime)
            .set(AVG_RETENTION, segment.avgRetention)
            .set(AVG_CTR, segment.avgCtr)
            .set(AVG_ENGAGEMENT, segment.avgEngagement)
            .set(GROWTH_RATE, segment.growthRate)
            .set(REVENUE_CONTRIBUTION, segment.revenueContribution)
            .set(TOP_CONTENT_CATEGORIES, segment.topContentCategories)
            .set(BEST_POSTING_TIMES, segment.bestPostingTimes)
            .set(UPDATED_AT, java.time.LocalDateTime.now())
            .where(ID.eq(segment.id))
            .execute()

        return findById(segment.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(AUDIENCE_SEGMENTS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toEntity(): AudienceSegment = AudienceSegment(
        id = get(ID),
        userId = get(USER_ID),
        name = get(NAME) ?: "",
        type = get(TYPE) ?: "CUSTOM",
        criteria = get(CRITERIA) ?: "{}",
        size = get(SIZE) ?: 0,
        percentage = get(PERCENTAGE) ?: 0.0,
        avgWatchTime = get(AVG_WATCH_TIME) ?: 0.0,
        avgRetention = get(AVG_RETENTION) ?: 0.0,
        avgCtr = get(AVG_CTR) ?: 0.0,
        avgEngagement = get(AVG_ENGAGEMENT) ?: 0.0,
        growthRate = get(GROWTH_RATE) ?: 0.0,
        revenueContribution = get(REVENUE_CONTRIBUTION) ?: 0.0,
        topContentCategories = get(TOP_CONTENT_CATEGORIES) ?: "[]",
        bestPostingTimes = get(BEST_POSTING_TIMES) ?: "[]",
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
