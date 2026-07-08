package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.aicalendar.AiCalendarRepository
import com.ongo.domain.aicalendar.AiContentCalendar
import com.ongo.infrastructure.persistence.jooq.Fields.CALENDAR_DATA
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.END_DATE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import org.jooq.impl.DSL
import com.ongo.infrastructure.persistence.jooq.Fields.START_DATE
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.AI_CONTENT_CALENDARS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class AiCalendarJooqRepository(
    private val dsl: DSLContext,
) : AiCalendarRepository {

    override fun findById(id: Long): AiContentCalendar? =
        dsl.select()
            .from(AI_CONTENT_CALENDARS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toAiCalendar()

    override fun findByUserId(userId: Long): List<AiContentCalendar> =
        dsl.select()
            .from(AI_CONTENT_CALENDARS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toAiCalendar() }

    override fun save(calendar: AiContentCalendar): AiContentCalendar {
        val id = dsl.insertInto(AI_CONTENT_CALENDARS)
            .set(USER_ID, calendar.userId)
            .set(TITLE, calendar.title)
            .set(START_DATE, calendar.startDate)
            .set(END_DATE, calendar.endDate)
            .set(DSL.field("settings", String::class.java), calendar.settings)
            .set(CALENDAR_DATA, calendar.calendarData)
            .set(STATUS, calendar.status)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return findById(id)!!
    }

    override fun update(calendar: AiContentCalendar): AiContentCalendar {
        dsl.update(AI_CONTENT_CALENDARS)
            .set(TITLE, calendar.title)
            .set(START_DATE, calendar.startDate)
            .set(END_DATE, calendar.endDate)
            .set(DSL.field("settings", String::class.java), calendar.settings)
            .set(CALENDAR_DATA, calendar.calendarData)
            .set(STATUS, calendar.status)
            .where(ID.eq(calendar.id))
            .execute()
        return findById(calendar.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(AI_CONTENT_CALENDARS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toAiCalendar(): AiContentCalendar = AiContentCalendar(
        id = get(ID),
        userId = get(USER_ID)!!,
        title = get(TITLE),
        startDate = localDate(START_DATE)!!,
        endDate = localDate(END_DATE)!!,
        settings = get(DSL.field("settings", String::class.java)),
        calendarData = get(CALENDAR_DATA) ?: "[]",
        status = get(STATUS) ?: "DRAFT",
        createdAt = localDateTime(CREATED_AT),
    )
}
