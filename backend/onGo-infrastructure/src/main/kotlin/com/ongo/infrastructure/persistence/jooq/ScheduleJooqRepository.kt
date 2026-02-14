package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.ScheduleStatus
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORMS
import com.ongo.infrastructure.persistence.jooq.Fields.SCHEDULED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Tables.SCHEDULES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ScheduleJooqRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) : ScheduleRepository {

    override fun findById(id: Long): Schedule? =
        dsl.select()
            .from(SCHEDULES)
            .where(ID.eq(id))
            .fetchOne()
            ?.toSchedule()

    override fun findByUserId(userId: Long): List<Schedule> =
        dsl.select()
            .from(SCHEDULES)
            .where(USER_ID.eq(userId))
            .orderBy(SCHEDULED_AT.asc())
            .fetch()
            .map { it.toSchedule() }

    override fun findByUserIdAndDateRange(
        userId: Long,
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<Schedule> =
        dsl.select()
            .from(SCHEDULES)
            .where(USER_ID.eq(userId))
            .and(SCHEDULED_AT.greaterOrEqual(from))
            .and(SCHEDULED_AT.lessThan(to))
            .orderBy(SCHEDULED_AT.asc())
            .fetch()
            .map { it.toSchedule() }

    override fun findDueSchedules(now: LocalDateTime): List<Schedule> =
        dsl.select()
            .from(SCHEDULES)
            .where(SCHEDULED_AT.lessOrEqual(now))
            .and(STATUS_TEXT.eq(ScheduleStatus.SCHEDULED.name))
            .orderBy(SCHEDULED_AT.asc())
            .forUpdate()
            .skipLocked()
            .fetch()
            .map { it.toSchedule() }

    override fun save(schedule: Schedule): Schedule {
        val platformsJson = JSONB.jsonb(objectMapper.writeValueAsString(schedule.platforms))

        val id = dsl.insertInto(SCHEDULES)
            .set(VIDEO_ID, schedule.videoId)
            .set(USER_ID, schedule.userId)
            .set(SCHEDULED_AT, schedule.scheduledAt)
            .set(STATUS, schedule.status.name)
            .set(DSL.field("platforms", JSONB::class.java), platformsJson)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(schedule: Schedule): Schedule {
        val platformsJson = JSONB.jsonb(objectMapper.writeValueAsString(schedule.platforms))

        dsl.update(SCHEDULES)
            .set(SCHEDULED_AT, schedule.scheduledAt)
            .set(STATUS, schedule.status.name)
            .set(DSL.field("platforms", JSONB::class.java), platformsJson)
            .where(ID.eq(schedule.id))
            .execute()

        return findById(schedule.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(SCHEDULES)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toSchedule(): Schedule {
        val platformsRaw = get("platforms")
        val platforms: Map<String, Any?> = try {
            when (platformsRaw) {
                is JSONB -> objectMapper.readValue(platformsRaw.data())
                is String -> objectMapper.readValue(platformsRaw)
                else -> emptyMap()
            }
        } catch (_: Exception) { emptyMap() }

        val statusStr = get(STATUS) ?: "SCHEDULED"
        return Schedule(
            id = get(ID),
            videoId = get(VIDEO_ID),
            userId = get(USER_ID),
            scheduledAt = localDateTime(SCHEDULED_AT)!!,
            status = try { ScheduleStatus.valueOf(statusStr) } catch (_: Exception) { ScheduleStatus.SCHEDULED },
            platforms = platforms,
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
