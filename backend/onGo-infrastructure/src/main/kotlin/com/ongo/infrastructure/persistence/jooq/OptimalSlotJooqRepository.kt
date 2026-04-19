package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.scheduleoptimizer.OptimalSlot
import com.ongo.domain.scheduleoptimizer.OptimalSlotRepository
import com.ongo.infrastructure.persistence.jooq.Fields.AUDIENCE_ONLINE
import com.ongo.infrastructure.persistence.jooq.Fields.COMPETITION_LEVEL
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DAY_OF_WEEK_STR
import com.ongo.infrastructure.persistence.jooq.Fields.HOUR
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.REASON
import com.ongo.infrastructure.persistence.jooq.Fields.SCORE_INT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.OPTIMAL_SLOTS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class OptimalSlotJooqRepository(
    private val dsl: DSLContext,
) : OptimalSlotRepository {

    override fun findByUserId(userId: Long): List<OptimalSlot> =
        dsl.select()
            .from(OPTIMAL_SLOTS)
            .where(USER_ID.eq(userId))
            .orderBy(SCORE_INT.desc())
            .fetch()
            .map { it.toOptimalSlot() }

    override fun findByPlatform(userId: Long, platform: String): List<OptimalSlot> =
        dsl.select()
            .from(OPTIMAL_SLOTS)
            .where(USER_ID.eq(userId))
            .and(PLATFORM.eq(platform))
            .orderBy(SCORE_INT.desc())
            .fetch()
            .map { it.toOptimalSlot() }

    override fun save(slot: OptimalSlot): OptimalSlot {
        val id = dsl.insertInto(OPTIMAL_SLOTS)
            .set(USER_ID, slot.userId)
            .set(PLATFORM, slot.platform)
            .set(DAY_OF_WEEK_STR, slot.dayOfWeek)
            .set(HOUR, slot.hour)
            .set(SCORE_INT, slot.score)
            .set(AUDIENCE_ONLINE, slot.audienceOnline)
            .set(COMPETITION_LEVEL, slot.competitionLevel)
            .set(REASON, slot.reason)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun deleteByUserIdAndPlatform(userId: Long, platform: String) {
        dsl.deleteFrom(OPTIMAL_SLOTS)
            .where(USER_ID.eq(userId))
            .and(PLATFORM.eq(platform))
            .execute()
    }

    override fun saveBatch(slots: List<OptimalSlot>): List<OptimalSlot> =
        slots.map { save(it) }

    private fun findById(id: Long): OptimalSlot? =
        dsl.select()
            .from(OPTIMAL_SLOTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toOptimalSlot()

    private fun Record.toOptimalSlot() = OptimalSlot(
        id = get(ID),
        userId = get(USER_ID),
        platform = get(PLATFORM) ?: "",
        dayOfWeek = get(DAY_OF_WEEK_STR) ?: "",
        hour = get(HOUR) ?: 0,
        score = get(SCORE_INT) ?: 0,
        audienceOnline = get(AUDIENCE_ONLINE) ?: 0,
        competitionLevel = get(COMPETITION_LEVEL) ?: "MEDIUM",
        reason = get(REASON) ?: "",
        createdAt = localDateTime(CREATED_AT),
    )
}
