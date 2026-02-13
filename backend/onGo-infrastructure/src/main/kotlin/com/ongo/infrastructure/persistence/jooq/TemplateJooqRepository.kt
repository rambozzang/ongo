package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.template.Template
import com.ongo.domain.template.TemplateRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CATEGORY
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION_TEMPLATE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.TAGS
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE_TEMPLATE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USAGE_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.TEMPLATES
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class TemplateJooqRepository(
    private val dsl: DSLContext,
) : TemplateRepository {

    override fun findById(id: Long): Template? =
        dsl.select()
            .from(TEMPLATES)
            .where(ID.eq(id))
            .fetchOne()
            ?.toTemplate()

    override fun findByUserId(userId: Long, page: Int, size: Int): List<Template> =
        dsl.select()
            .from(TEMPLATES)
            .where(USER_ID.eq(userId))
            .orderBy(UPDATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toTemplate() }

    override fun countByUserId(userId: Long): Int =
        dsl.selectCount()
            .from(TEMPLATES)
            .where(USER_ID.eq(userId))
            .fetchOne(0, Int::class.java) ?: 0

    override fun save(template: Template): Template {
        val record = dsl.insertInto(TEMPLATES)
            .set(USER_ID, template.userId)
            .set(NAME, template.name)
            .set(TITLE_TEMPLATE, template.titleTemplate)
            .set(DESCRIPTION_TEMPLATE, template.descriptionTemplate)
            .set(TAGS, template.tags.toTypedArray())
            .set(CATEGORY, template.category)
            .set(PLATFORM, template.platform)
            .set(USAGE_COUNT, template.usageCount)
            .returning()
            .fetchOne()!!

        return record.toTemplate()
    }

    override fun update(template: Template): Template {
        val record = dsl.update(TEMPLATES)
            .set(NAME, template.name)
            .set(TITLE_TEMPLATE, template.titleTemplate)
            .set(DESCRIPTION_TEMPLATE, template.descriptionTemplate)
            .set(TAGS, template.tags.toTypedArray())
            .set(CATEGORY, template.category)
            .set(PLATFORM, template.platform)
            .where(ID.eq(template.id))
            .returning()
            .fetchOne()!!

        return record.toTemplate()
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TEMPLATES)
            .where(ID.eq(id))
            .execute()
    }

    override fun incrementUsageCount(id: Long) {
        dsl.update(TEMPLATES)
            .set(USAGE_COUNT, DSL.field("usage_count + 1", Int::class.java))
            .where(ID.eq(id))
            .execute()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Record.toTemplate(): Template = Template(
        id = get(ID),
        userId = get(USER_ID),
        name = get(NAME),
        titleTemplate = get(TITLE_TEMPLATE),
        descriptionTemplate = get(DESCRIPTION_TEMPLATE),
        tags = (get(TAGS) as? Array<String>)?.toList() ?: emptyList(),
        category = get(CATEGORY),
        platform = get(PLATFORM),
        usageCount = get(USAGE_COUNT) ?: 0,
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
