package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.aicalendar.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
class AiCalendarJooqRepository(
    private val dsl: DSLContext,
) : AiCalendarRepository {

    companion object {
        private val TABLE = DSL.table("ai_content_calendars")
        private val TITLE = DSL.field("title", String::class.java)
        private val START_DATE = DSL.field("start_date", LocalDate::class.java)
        private val END_DATE = DSL.field("end_date", LocalDate::class.java)
        private val SETTINGS = DSL.field("settings", String::class.java)
        private val CALENDAR_DATA = DSL.field("calendar_data", String::class.java)
    }

    override fun findById(id: Long): AiContentCalendar? =
        dsl.select().from(TABLE)
            .where(ID.eq(id))
            .fetchOne()?.toCalendar()

    override fun findByUserId(userId: Long): List<AiContentCalendar> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .fetch { it.toCalendar() }

    override fun save(calendar: AiContentCalendar): AiContentCalendar {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, calendar.userId)
            .set(TITLE, calendar.title)
            .set(START_DATE, calendar.startDate)
            .set(END_DATE, calendar.endDate)
            .set(SETTINGS, DSL.field("?::jsonb", String::class.java, calendar.settings))
            .set(CALENDAR_DATA, DSL.field("?::jsonb", String::class.java, calendar.calendarData))
            .set(STATUS, calendar.status)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun update(calendar: AiContentCalendar): AiContentCalendar {
        dsl.update(TABLE)
            .set(TITLE, calendar.title)
            .set(START_DATE, calendar.startDate)
            .set(END_DATE, calendar.endDate)
            .set(SETTINGS, DSL.field("?::jsonb", String::class.java, calendar.settings))
            .set(CALENDAR_DATA, DSL.field("?::jsonb", String::class.java, calendar.calendarData))
            .set(STATUS, calendar.status)
            .where(ID.eq(calendar.id))
            .execute()

        return findById(calendar.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toCalendar(): AiContentCalendar {
        val settingsRaw = get("settings")
        val calendarDataRaw = get("calendar_data")
        return AiContentCalendar(
            id = get(ID),
            userId = get(USER_ID),
            title = get(TITLE),
            startDate = localDate(START_DATE)!!,
            endDate = localDate(END_DATE)!!,
            settings = when (settingsRaw) {
                is String -> settingsRaw
                else -> settingsRaw?.toString()
            },
            calendarData = when (calendarDataRaw) {
                is String -> calendarDataRaw
                else -> calendarDataRaw?.toString() ?: "{}"
            },
            status = get(STATUS) ?: "DRAFT",
            createdAt = localDateTime(CREATED_AT),
        )
    }
}
