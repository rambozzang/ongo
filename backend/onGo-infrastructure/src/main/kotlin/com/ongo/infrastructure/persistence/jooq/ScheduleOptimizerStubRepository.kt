package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.scheduleoptimizer.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ScheduleRecommendationJooqRepository(
    private val dsl: DSLContext,
) : ScheduleRecommendationRepository {

    companion object {
        private val TABLE = DSL.table("schedule_recommendations")
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
        private val CURRENT_SCHEDULE = DSL.field("current_schedule", LocalDateTime::class.java)
        private val RECOMMENDED_SCHEDULE = DSL.field("recommended_schedule", LocalDateTime::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val EXPECTED_IMPROVEMENT = DSL.field("expected_improvement", Int::class.java)
        private val CONFIDENCE = DSL.field("confidence", Int::class.java)
    }

    override fun findById(id: Long): ScheduleRecommendation? =
        dsl.select().from(TABLE)
            .where(ID.eq(id))
            .fetchOne()?.toRecommendation()

    override fun findByUserId(userId: Long): List<ScheduleRecommendation> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .fetch { it.toRecommendation() }

    override fun save(rec: ScheduleRecommendation): ScheduleRecommendation {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, rec.userId)
            .set(VIDEO_ID, rec.videoId)
            .set(VIDEO_TITLE, rec.videoTitle)
            .set(CURRENT_SCHEDULE, rec.currentSchedule)
            .set(RECOMMENDED_SCHEDULE, rec.recommendedSchedule)
            .set(PLATFORM, rec.platform)
            .set(EXPECTED_IMPROVEMENT, rec.expectedImprovement)
            .set(CONFIDENCE, rec.confidence)
            .set(STATUS, rec.status)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: String) {
        dsl.update(TABLE)
            .set(STATUS, status)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toRecommendation(): ScheduleRecommendation =
        ScheduleRecommendation(
            id = get(ID),
            userId = get(USER_ID),
            videoId = get(VIDEO_ID),
            videoTitle = get(VIDEO_TITLE),
            currentSchedule = localDateTime(CURRENT_SCHEDULE),
            recommendedSchedule = localDateTime(RECOMMENDED_SCHEDULE)!!,
            platform = get(PLATFORM),
            expectedImprovement = get(EXPECTED_IMPROVEMENT) ?: 0,
            confidence = get(CONFIDENCE) ?: 0,
            status = get(STATUS) ?: "PENDING",
            createdAt = localDateTime(CREATED_AT),
        )
}

@Repository
class OptimalSlotJooqRepository(
    private val dsl: DSLContext,
) : OptimalSlotRepository {

    companion object {
        private val TABLE = DSL.table("optimal_slots")
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val DAY_OF_WEEK = DSL.field("day_of_week", String::class.java)
        private val HOUR = DSL.field("hour", Int::class.java)
        private val SCORE = DSL.field("score", Int::class.java)
        private val AUDIENCE_ONLINE = DSL.field("audience_online", Int::class.java)
        private val COMPETITION_LEVEL = DSL.field("competition_level", String::class.java)
        private val REASON = DSL.field("reason", String::class.java)
    }

    override fun findByUserId(userId: Long): List<OptimalSlot> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .fetch { it.toSlot() }

    override fun findByPlatform(userId: Long, platform: String): List<OptimalSlot> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .and(PLATFORM.eq(platform))
            .fetch { it.toSlot() }

    override fun save(slot: OptimalSlot): OptimalSlot {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, slot.userId)
            .set(PLATFORM, slot.platform)
            .set(DAY_OF_WEEK, slot.dayOfWeek)
            .set(HOUR, slot.hour)
            .set(SCORE, slot.score)
            .set(AUDIENCE_ONLINE, slot.audienceOnline)
            .set(COMPETITION_LEVEL, slot.competitionLevel)
            .set(REASON, slot.reason)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toSlot()
    }

    private fun Record.toSlot(): OptimalSlot =
        OptimalSlot(
            id = get(ID),
            userId = get(USER_ID),
            platform = get(PLATFORM),
            dayOfWeek = get(DAY_OF_WEEK),
            hour = get(HOUR) ?: 0,
            score = get(SCORE) ?: 0,
            audienceOnline = get(AUDIENCE_ONLINE) ?: 0,
            competitionLevel = get(COMPETITION_LEVEL) ?: "MEDIUM",
            reason = get(REASON),
            createdAt = localDateTime(CREATED_AT),
        )
}
