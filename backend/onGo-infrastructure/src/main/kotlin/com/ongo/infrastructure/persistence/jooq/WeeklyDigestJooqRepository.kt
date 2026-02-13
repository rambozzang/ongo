package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ai.WeeklyDigest
import com.ongo.domain.ai.WeeklyDigestRepository
import com.ongo.infrastructure.persistence.jooq.Fields.ACTION_ITEMS
import com.ongo.infrastructure.persistence.jooq.Fields.ANOMALIES
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.GENERATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.SUMMARY
import com.ongo.infrastructure.persistence.jooq.Fields.TOP_VIDEOS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.WEEK_END_DATE
import com.ongo.infrastructure.persistence.jooq.Fields.WEEK_START_DATE
import com.ongo.infrastructure.persistence.jooq.Tables.WEEKLY_DIGESTS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository

@Primary
@Repository
class WeeklyDigestJooqRepository(
    private val dsl: DSLContext,
) : WeeklyDigestRepository {

    override fun findById(id: Long): WeeklyDigest? =
        dsl.select()
            .from(WEEKLY_DIGESTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toWeeklyDigest()

    override fun findLatestByUserId(userId: Long): WeeklyDigest? =
        dsl.select()
            .from(WEEKLY_DIGESTS)
            .where(USER_ID.eq(userId))
            .orderBy(GENERATED_AT.desc())
            .limit(1)
            .fetchOne()
            ?.toWeeklyDigest()

    override fun findByUserId(userId: Long, page: Int, size: Int): List<WeeklyDigest> =
        dsl.select()
            .from(WEEKLY_DIGESTS)
            .where(USER_ID.eq(userId))
            .orderBy(GENERATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toWeeklyDigest() }

    override fun countByUserId(userId: Long): Int =
        dsl.selectCount()
            .from(WEEKLY_DIGESTS)
            .where(USER_ID.eq(userId))
            .fetchOne(0, Int::class.java) ?: 0

    override fun save(digest: WeeklyDigest): WeeklyDigest {
        val record = dsl.insertInto(WEEKLY_DIGESTS)
            .set(USER_ID, digest.userId)
            .set(WEEK_START_DATE, digest.weekStartDate)
            .set(WEEK_END_DATE, digest.weekEndDate)
            .set(SUMMARY, digest.summary)
            .set(TOP_VIDEOS, digest.topVideos)
            .set(ANOMALIES, digest.anomalies)
            .set(ACTION_ITEMS, digest.actionItems)
            .set(GENERATED_AT, digest.generatedAt)
            .returning()
            .fetchOne()!!

        return record.toWeeklyDigest()
    }

    private fun Record.toWeeklyDigest(): WeeklyDigest = WeeklyDigest(
        id = get(ID),
        userId = get(USER_ID),
        weekStartDate = localDate(WEEK_START_DATE)!!,
        weekEndDate = localDate(WEEK_END_DATE)!!,
        summary = get(SUMMARY),
        topVideos = get(TOP_VIDEOS) ?: "",
        anomalies = get(ANOMALIES) ?: "",
        actionItems = get(ACTION_ITEMS) ?: "",
        generatedAt = localDateTime(GENERATED_AT) ?: java.time.LocalDateTime.now(),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
