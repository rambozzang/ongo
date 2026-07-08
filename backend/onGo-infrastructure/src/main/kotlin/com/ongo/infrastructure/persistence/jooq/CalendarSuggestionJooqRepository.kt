package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentcalendarai.CalendarSuggestion
import com.ongo.domain.contentcalendarai.CalendarSuggestionRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CONFIDENCE
import com.ongo.infrastructure.persistence.jooq.Fields.CONTENT_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.EXPECTED_ENGAGEMENT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.SUGGESTED_DATE
import com.ongo.infrastructure.persistence.jooq.Fields.SUGGESTED_TIME
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.TOPIC
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import com.ongo.infrastructure.persistence.jooq.Tables.CALENDAR_SUGGESTIONS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class CalendarSuggestionJooqRepository(
    private val dsl: DSLContext,
) : CalendarSuggestionRepository {

    override fun findByWorkspaceId(workspaceId: Long): List<CalendarSuggestion> =
        dsl.select()
            .from(CALENDAR_SUGGESTIONS)
            .where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(SUGGESTED_DATE.asc(), SUGGESTED_TIME.asc())
            .fetch()
            .map { it.toSuggestion() }

    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<CalendarSuggestion> =
        dsl.select()
            .from(CALENDAR_SUGGESTIONS)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(STATUS.eq(status))
            .orderBy(SUGGESTED_DATE.asc(), SUGGESTED_TIME.asc())
            .fetch()
            .map { it.toSuggestion() }

    override fun findById(id: Long): CalendarSuggestion? =
        dsl.select()
            .from(CALENDAR_SUGGESTIONS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toSuggestion()

    override fun save(entity: CalendarSuggestion): CalendarSuggestion {
        val id = dsl.insertInto(CALENDAR_SUGGESTIONS)
            .set(WORKSPACE_ID, entity.workspaceId)
            .set(TITLE, entity.title)
            .set(DESCRIPTION, entity.description)
            .set(SUGGESTED_DATE, entity.suggestedDate)
            .set(SUGGESTED_TIME, entity.suggestedTime)
            .set(PLATFORM, entity.platform)
            .set(CONTENT_TYPE, entity.contentType)
            .set(TOPIC, entity.topic)
            .set(EXPECTED_ENGAGEMENT, entity.expectedEngagement)
            .set(DSL.field("confidence", Int::class.java), entity.confidence)
            .set(STATUS, entity.status)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return findById(id)!!
    }

    override fun saveBatch(entities: List<CalendarSuggestion>): List<CalendarSuggestion> {
        return entities.map { save(it) }
    }

    override fun updateStatus(id: Long, status: String) {
        dsl.update(CALENDAR_SUGGESTIONS)
            .set(STATUS, status)
            .set(UPDATED_AT, java.time.LocalDateTime.now())
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toSuggestion(): CalendarSuggestion = CalendarSuggestion(
        id = get(ID) ?: 0,
        workspaceId = get(WORKSPACE_ID)!!,
        title = get(TITLE)!!,
        description = get(DESCRIPTION),
        suggestedDate = localDate(SUGGESTED_DATE)!!,
        suggestedTime = get(SUGGESTED_TIME)!!,
        platform = get(PLATFORM)!!,
        contentType = get(CONTENT_TYPE)!!,
        topic = get(TOPIC),
        expectedEngagement = get(EXPECTED_ENGAGEMENT) ?: java.math.BigDecimal.ZERO,
        confidence = get(DSL.field("confidence", Int::class.java)) ?: 0,
        status = get(STATUS) ?: "PENDING",
        createdAt = localDateTime(CREATED_AT) ?: java.time.LocalDateTime.now(),
        updatedAt = localDateTime(UPDATED_AT) ?: java.time.LocalDateTime.now(),
    )
}
