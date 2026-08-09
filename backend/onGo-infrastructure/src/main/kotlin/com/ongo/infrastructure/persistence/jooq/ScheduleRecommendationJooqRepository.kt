package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.scheduleoptimizer.ScheduleRecommendation
import com.ongo.domain.scheduleoptimizer.ScheduleRecommendationRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.EXPECTED_IMPROVEMENT
import com.ongo.infrastructure.persistence.jooq.Fields.CURRENT_SCHEDULE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.RECOMMENDED_SCHEDULE
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_TITLE
import com.ongo.infrastructure.persistence.jooq.Tables.SCHEDULE_RECOMMENDATIONS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class ScheduleRecommendationJooqRepository(
    private val dsl: DSLContext,
) : ScheduleRecommendationRepository {

    private companion object {
        val RECOMMENDATION_CONFIDENCE = DSL.field("confidence", Int::class.java)
        val RECOMMENDATION_CHANNEL_ID = DSL.field("channel_id", Long::class.java)
    }

    override fun findByIdAndUserId(id: Long, userId: Long): ScheduleRecommendation? =
        dsl.select()
            .from(SCHEDULE_RECOMMENDATIONS)
            .where(ID.eq(id))
            .and(USER_ID.eq(userId))
            .fetchOne()
            ?.toScheduleRecommendation()

    override fun findByUserId(userId: Long): List<ScheduleRecommendation> =
        dsl.select()
            .from(SCHEDULE_RECOMMENDATIONS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toScheduleRecommendation() }

    override fun save(rec: ScheduleRecommendation): ScheduleRecommendation {
        val id = dsl.insertInto(SCHEDULE_RECOMMENDATIONS)
            .set(USER_ID, rec.userId)
            .set(VIDEO_ID, rec.videoId)
            .set(RECOMMENDATION_CHANNEL_ID, rec.channelId)
            .set(VIDEO_TITLE, rec.videoTitle)
            .set(CURRENT_SCHEDULE, rec.currentSchedule)
            .set(RECOMMENDED_SCHEDULE, rec.recommendedSchedule)
            .set(PLATFORM, rec.platform)
            .set(EXPECTED_IMPROVEMENT, rec.expectedImprovement)
            .set(RECOMMENDATION_CONFIDENCE, rec.confidence)
            .set(STATUS, rec.status)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findByIdAndUserId(id, rec.userId)!!
    }

    override fun updateStatus(id: Long, userId: Long, status: String): Boolean =
        dsl.update(SCHEDULE_RECOMMENDATIONS)
            .set(STATUS, status)
            .where(ID.eq(id))
            .and(USER_ID.eq(userId))
            .execute() == 1

    private fun Record.toScheduleRecommendation() = ScheduleRecommendation(
        id = get(ID),
        userId = get(USER_ID),
        videoId = get(VIDEO_ID),
        channelId = get(RECOMMENDATION_CHANNEL_ID),
        videoTitle = get(VIDEO_TITLE) ?: "",
        currentSchedule = localDateTime(CURRENT_SCHEDULE),
        recommendedSchedule = localDateTime(RECOMMENDED_SCHEDULE)
            ?: throw IllegalStateException("schedule_recommendations.recommended_schedule is null"),
        platform = get(PLATFORM) ?: "",
        expectedImprovement = get(EXPECTED_IMPROVEMENT) ?: 0,
        confidence = get(RECOMMENDATION_CONFIDENCE) ?: 0,
        status = get(STATUS) ?: "PENDING",
        createdAt = localDateTime(CREATED_AT),
    )
}
