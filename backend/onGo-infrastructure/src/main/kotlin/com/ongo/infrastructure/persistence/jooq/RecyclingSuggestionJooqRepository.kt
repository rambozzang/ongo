package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.recycling.RecyclingSuggestion
import com.ongo.domain.recycling.RecyclingSuggestionRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Tables.RECYCLING_SUGGESTIONS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class RecyclingSuggestionJooqRepository(
    private val dsl: DSLContext,
) : RecyclingSuggestionRepository {

    companion object {
        val SUGGESTION_TYPE = DSL.field("suggestion_type", String::class.java)
        val REASON = DSL.field("reason", String::class.java)
        val SUGGESTED_PLATFORMS = DSL.field("suggested_platforms", Array<String>::class.java)
        val PRIORITY_SCORE = DSL.field("priority_score", Int::class.java)
    }

    override fun findByUserId(userId: Long, status: String?): List<RecyclingSuggestion> {
        var query = dsl.select()
            .from(RECYCLING_SUGGESTIONS)
            .where(USER_ID.eq(userId))

        if (status != null) {
            query = query.and(STATUS.eq(status))
        }

        return query
            .orderBy(PRIORITY_SCORE.desc(), CREATED_AT.desc())
            .fetch()
            .map { it.toRecyclingSuggestion() }
    }

    override fun findById(id: Long): RecyclingSuggestion? =
        dsl.select()
            .from(RECYCLING_SUGGESTIONS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toRecyclingSuggestion()

    override fun save(suggestion: RecyclingSuggestion): RecyclingSuggestion {
        val record = dsl.insertInto(RECYCLING_SUGGESTIONS)
            .set(USER_ID, suggestion.userId)
            .set(VIDEO_ID, suggestion.videoId)
            .set(SUGGESTION_TYPE, suggestion.suggestionType)
            .set(REASON, suggestion.reason)
            .set(SUGGESTED_PLATFORMS, suggestion.suggestedPlatforms.toTypedArray())
            .set(PRIORITY_SCORE, suggestion.priorityScore)
            .set(STATUS, suggestion.status)
            .returning()
            .fetchOne()!!

        return record.toRecyclingSuggestion()
    }

    override fun saveAll(suggestions: List<RecyclingSuggestion>): List<RecyclingSuggestion> {
        return suggestions.map { save(it) }
    }

    override fun updateStatus(id: Long, status: String): RecyclingSuggestion? {
        val record = dsl.update(RECYCLING_SUGGESTIONS)
            .set(STATUS, status)
            .where(ID.eq(id))
            .returning()
            .fetchOne()

        return record?.toRecyclingSuggestion()
    }

    override fun deleteByUserId(userId: Long) {
        dsl.deleteFrom(RECYCLING_SUGGESTIONS)
            .where(USER_ID.eq(userId))
            .execute()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Record.toRecyclingSuggestion(): RecyclingSuggestion {
        val platforms = get(SUGGESTED_PLATFORMS.name)
        val platformList = when (platforms) {
            is Array<*> -> (platforms as Array<String>).toList()
            else -> emptyList()
        }

        return RecyclingSuggestion(
            id = get(ID),
            userId = get(USER_ID),
            videoId = get(VIDEO_ID),
            suggestionType = get(SUGGESTION_TYPE),
            reason = get(REASON),
            suggestedPlatforms = platformList,
            priorityScore = get(PRIORITY_SCORE) ?: 50,
            status = get(STATUS) ?: "PENDING",
            createdAt = localDateTime(CREATED_AT),
        )
    }
}
