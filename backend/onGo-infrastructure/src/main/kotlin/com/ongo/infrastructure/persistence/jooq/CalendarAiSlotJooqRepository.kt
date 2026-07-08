package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentcalendarai.CalendarAiSlot
import com.ongo.domain.contentcalendarai.CalendarAiSlotRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.REASON
import com.ongo.infrastructure.persistence.jooq.Fields.SCORE_INT
import com.ongo.infrastructure.persistence.jooq.Fields.SLOT_DATE
import com.ongo.infrastructure.persistence.jooq.Fields.SLOT_TIME
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import com.ongo.infrastructure.persistence.jooq.Tables.CALENDAR_AI_SLOTS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class CalendarAiSlotJooqRepository(
    private val dsl: DSLContext,
) : CalendarAiSlotRepository {

    override fun findByWorkspaceId(workspaceId: Long): List<CalendarAiSlot> =
        dsl.select()
            .from(CALENDAR_AI_SLOTS)
            .where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(SLOT_DATE.asc(), SLOT_TIME.asc())
            .fetch()
            .map { it.toSlot() }

    override fun findByWorkspaceIdAndDateBetween(workspaceId: Long, startDate: LocalDate, endDate: LocalDate): List<CalendarAiSlot> =
        dsl.select()
            .from(CALENDAR_AI_SLOTS)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(SLOT_DATE.greaterOrEqual(startDate))
            .and(SLOT_DATE.lessOrEqual(endDate))
            .orderBy(SLOT_DATE.asc(), SLOT_TIME.asc())
            .fetch()
            .map { it.toSlot() }

    private fun Record.toSlot(): CalendarAiSlot = CalendarAiSlot(
        id = get(ID) ?: 0,
        workspaceId = get(WORKSPACE_ID)!!,
        slotDate = localDate(SLOT_DATE)!!,
        slotTime = get(SLOT_TIME)!!,
        platform = get(PLATFORM)!!,
        score = get(SCORE_INT) ?: 0,
        reason = get(REASON),
        createdAt = localDateTime(CREATED_AT) ?: java.time.LocalDateTime.now(),
    )
}
