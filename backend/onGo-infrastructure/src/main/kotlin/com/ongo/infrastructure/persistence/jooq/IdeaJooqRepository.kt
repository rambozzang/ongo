package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.idea.Idea
import com.ongo.domain.idea.IdeaRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CATEGORY
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.DUE_DATE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PRIORITY
import com.ongo.infrastructure.persistence.jooq.Fields.REFERENCE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.SOURCE
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TAGS
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.IDEAS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class IdeaJooqRepository(
    private val dsl: DSLContext,
) : IdeaRepository {

    override fun findById(id: Long): Idea? =
        dsl.select()
            .from(IDEAS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toIdea()

    override fun findByUserId(userId: Long, status: String?, category: String?, priority: String?): List<Idea> {
        var query = dsl.select()
            .from(IDEAS)
            .where(USER_ID.eq(userId))

        if (status != null) {
            query = query.and(STATUS.eq(status))
        }
        if (category != null) {
            query = query.and(CATEGORY.eq(category))
        }
        if (priority != null) {
            query = query.and(PRIORITY.eq(priority))
        }

        return query.orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toIdea() }
    }

    override fun save(idea: Idea): Idea {
        val id = dsl.insertInto(IDEAS)
            .set(USER_ID, idea.userId)
            .set(TITLE, idea.title)
            .set(DESCRIPTION, idea.description)
            .set(STATUS, idea.status)
            .set(CATEGORY, idea.category)
            .set(DSL.field("tags", Array<String>::class.java), idea.tags)
            .set(PRIORITY, idea.priority)
            .set(SOURCE, idea.source)
            .set(REFERENCE_URL, idea.referenceUrl)
            .set(DUE_DATE, idea.dueDate)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(idea: Idea): Idea {
        dsl.update(IDEAS)
            .set(TITLE, idea.title)
            .set(DESCRIPTION, idea.description)
            .set(STATUS, idea.status)
            .set(CATEGORY, idea.category)
            .set(DSL.field("tags", Array<String>::class.java), idea.tags)
            .set(PRIORITY, idea.priority)
            .set(SOURCE, idea.source)
            .set(REFERENCE_URL, idea.referenceUrl)
            .set(DUE_DATE, idea.dueDate)
            .set(UPDATED_AT, java.time.LocalDateTime.now())
            .where(ID.eq(idea.id))
            .execute()

        return findById(idea.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(IDEAS)
            .where(ID.eq(id))
            .execute()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Record.toIdea(): Idea {
        val tagsRaw = get("tags")
        val tags: Array<String> = when (tagsRaw) {
            is Array<*> -> tagsRaw as Array<String>
            is java.sql.Array -> tagsRaw.array as Array<String>
            else -> emptyArray()
        }

        return Idea(
            id = get(ID),
            userId = get(USER_ID),
            title = get(TITLE),
            description = get(DESCRIPTION),
            status = get(STATUS),
            category = get(CATEGORY),
            tags = tags,
            priority = get(PRIORITY),
            source = get(SOURCE),
            referenceUrl = get(REFERENCE_URL),
            dueDate = localDate(DUE_DATE),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
