package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.recurring.RecurringSchedule
import com.ongo.domain.recurring.RecurringScheduleRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DAY_OF_MONTH
import com.ongo.infrastructure.persistence.jooq.Fields.DAY_OF_WEEK
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION_TEMPLATE
import com.ongo.infrastructure.persistence.jooq.Fields.FREQUENCY
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IS_ACTIVE
import com.ongo.infrastructure.persistence.jooq.Fields.LAST_RUN_AT
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.NEXT_RUN_AT
import com.ongo.infrastructure.persistence.jooq.Fields.TAGS
import com.ongo.infrastructure.persistence.jooq.Fields.TIME_OF_DAY
import com.ongo.infrastructure.persistence.jooq.Fields.TIMEZONE
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE_TEMPLATE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.RECURRING_SCHEDULES
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalTime

@Repository
class RecurringScheduleJooqRepository(
    private val dsl: DSLContext,
) : RecurringScheduleRepository {

    companion object {
        private val PLATFORMS_FIELD = DSL.field("platforms", Array<String>::class.java)
        private val TAGS_FIELD = DSL.field("tags", Array<String>::class.java)
    }

    override fun findById(id: Long): RecurringSchedule? =
        dsl.select()
            .from(RECURRING_SCHEDULES)
            .where(ID.eq(id))
            .fetchOne()
            ?.toRecurringSchedule()

    override fun findByUserId(userId: Long): List<RecurringSchedule> =
        dsl.select()
            .from(RECURRING_SCHEDULES)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toRecurringSchedule() }

    override fun save(schedule: RecurringSchedule): RecurringSchedule {
        val id = dsl.insertInto(RECURRING_SCHEDULES)
            .set(USER_ID, schedule.userId)
            .set(NAME, schedule.name)
            .set(FREQUENCY, schedule.frequency)
            .set(DAY_OF_WEEK, schedule.dayOfWeek)
            .set(DAY_OF_MONTH, schedule.dayOfMonth)
            .set(TIME_OF_DAY, schedule.timeOfDay)
            .set(TIMEZONE, schedule.timezone)
            .set(PLATFORMS_FIELD, schedule.platforms.toTypedArray())
            .set(TITLE_TEMPLATE, schedule.titleTemplate)
            .set(DESCRIPTION_TEMPLATE, schedule.descriptionTemplate)
            .set(TAGS_FIELD, schedule.tags.toTypedArray())
            .set(IS_ACTIVE, schedule.isActive)
            .set(NEXT_RUN_AT, schedule.nextRunAt)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(schedule: RecurringSchedule): RecurringSchedule {
        dsl.update(RECURRING_SCHEDULES)
            .set(NAME, schedule.name)
            .set(FREQUENCY, schedule.frequency)
            .set(DAY_OF_WEEK, schedule.dayOfWeek)
            .set(DAY_OF_MONTH, schedule.dayOfMonth)
            .set(TIME_OF_DAY, schedule.timeOfDay)
            .set(TIMEZONE, schedule.timezone)
            .set(PLATFORMS_FIELD, schedule.platforms.toTypedArray())
            .set(TITLE_TEMPLATE, schedule.titleTemplate)
            .set(DESCRIPTION_TEMPLATE, schedule.descriptionTemplate)
            .set(TAGS_FIELD, schedule.tags.toTypedArray())
            .set(IS_ACTIVE, schedule.isActive)
            .set(NEXT_RUN_AT, schedule.nextRunAt)
            .set(LAST_RUN_AT, schedule.lastRunAt)
            .where(ID.eq(schedule.id))
            .execute()

        return findById(schedule.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(RECURRING_SCHEDULES)
            .where(ID.eq(id))
            .execute()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Record.toRecurringSchedule(): RecurringSchedule {
        val platformsRaw = get("platforms")
        val platforms: List<String> = when (platformsRaw) {
            is Array<*> -> (platformsRaw as Array<String>).toList()
            is java.sql.Array -> (platformsRaw.array as Array<String>).toList()
            else -> emptyList()
        }

        val tagsRaw = get("tags")
        val tags: List<String> = when (tagsRaw) {
            is Array<*> -> (tagsRaw as Array<String>).toList()
            is java.sql.Array -> (tagsRaw.array as Array<String>).toList()
            else -> emptyList()
        }

        val timeRaw = get("time_of_day")
        val timeOfDay: LocalTime = when (timeRaw) {
            is LocalTime -> timeRaw
            is java.sql.Time -> timeRaw.toLocalTime()
            else -> LocalTime.MIDNIGHT
        }

        return RecurringSchedule(
            id = get(ID),
            userId = get(USER_ID),
            name = get(NAME),
            frequency = get(FREQUENCY),
            dayOfWeek = get(DAY_OF_WEEK),
            dayOfMonth = get(DAY_OF_MONTH),
            timeOfDay = timeOfDay,
            timezone = get(TIMEZONE) ?: "Asia/Seoul",
            platforms = platforms,
            titleTemplate = get(TITLE_TEMPLATE),
            descriptionTemplate = get(DESCRIPTION_TEMPLATE),
            tags = tags,
            isActive = get(IS_ACTIVE) ?: true,
            nextRunAt = localDateTime(NEXT_RUN_AT),
            lastRunAt = localDateTime(LAST_RUN_AT),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
