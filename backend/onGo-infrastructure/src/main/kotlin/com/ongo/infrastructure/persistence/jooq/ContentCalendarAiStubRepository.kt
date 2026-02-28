package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentcalendarai.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
class CalendarAiSlotJooqRepository(
    private val dsl: DSLContext,
) : CalendarAiSlotRepository {

    companion object {
        private val TABLE = DSL.table("calendar_ai_slots")
        private val SLOT_DATE = DSL.field("slot_date", LocalDate::class.java)
        private val SLOT_TIME = DSL.field("slot_time", String::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val SCORE = DSL.field("score", Int::class.java)
        private val REASON = DSL.field("reason", String::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<CalendarAiSlot> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch { it.toSlot() }

    override fun findByWorkspaceIdAndDateBetween(workspaceId: Long, startDate: LocalDate, endDate: LocalDate): List<CalendarAiSlot> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(SLOT_DATE.ge(startDate))
            .and(SLOT_DATE.le(endDate))
            .fetch { it.toSlot() }

    private fun Record.toSlot(): CalendarAiSlot =
        CalendarAiSlot(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            slotDate = localDate(SLOT_DATE)!!,
            slotTime = get(SLOT_TIME),
            platform = get(PLATFORM),
            score = get(SCORE) ?: 0,
            reason = get(REASON),
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
        )
}

@Repository
class CalendarSuggestionJooqRepository(
    private val dsl: DSLContext,
) : CalendarSuggestionRepository {

    companion object {
        private val TABLE = DSL.table("calendar_suggestions")
        private val TITLE = DSL.field("title", String::class.java)
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val SUGGESTED_DATE = DSL.field("suggested_date", LocalDate::class.java)
        private val SUGGESTED_TIME = DSL.field("suggested_time", String::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val CONTENT_TYPE = DSL.field("content_type", String::class.java)
        private val TOPIC = DSL.field("topic", String::class.java)
        private val EXPECTED_ENGAGEMENT = DSL.field("expected_engagement", BigDecimal::class.java)
        private val CONFIDENCE = DSL.field("confidence", Int::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<CalendarSuggestion> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch { it.toSuggestion() }

    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<CalendarSuggestion> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(STATUS.eq(status))
            .fetch { it.toSuggestion() }

    override fun findById(id: Long): CalendarSuggestion? =
        dsl.select().from(TABLE)
            .where(ID.eq(id))
            .fetchOne()?.toSuggestion()

    override fun save(entity: CalendarSuggestion): CalendarSuggestion {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, entity.workspaceId)
            .set(TITLE, entity.title)
            .set(DESCRIPTION, entity.description)
            .set(SUGGESTED_DATE, entity.suggestedDate)
            .set(SUGGESTED_TIME, entity.suggestedTime)
            .set(PLATFORM, entity.platform)
            .set(CONTENT_TYPE, entity.contentType)
            .set(TOPIC, entity.topic)
            .set(EXPECTED_ENGAGEMENT, entity.expectedEngagement)
            .set(CONFIDENCE, entity.confidence)
            .set(STATUS, entity.status)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun saveBatch(entities: List<CalendarSuggestion>): List<CalendarSuggestion> =
        entities.map { save(it) }

    override fun updateStatus(id: Long, status: String) {
        dsl.update(TABLE)
            .set(STATUS, status)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toSuggestion(): CalendarSuggestion =
        CalendarSuggestion(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            title = get(TITLE),
            description = get(DESCRIPTION),
            suggestedDate = localDate(SUGGESTED_DATE)!!,
            suggestedTime = get(SUGGESTED_TIME),
            platform = get(PLATFORM),
            contentType = get(CONTENT_TYPE),
            topic = get(TOPIC),
            expectedEngagement = get(EXPECTED_ENGAGEMENT) ?: BigDecimal.ZERO,
            confidence = get(CONFIDENCE) ?: 0,
            status = get(STATUS) ?: "PENDING",
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
            updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
        )
}
