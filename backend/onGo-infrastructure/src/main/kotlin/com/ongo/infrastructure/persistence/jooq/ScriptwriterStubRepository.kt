package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.scriptwriter.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ScriptJooqRepository(
    private val dsl: DSLContext,
) : ScriptRepository {

    companion object {
        private val TABLE = DSL.table("scripts")
        private val TOPIC = DSL.field("topic", String::class.java)
        private val FORMAT = DSL.field("format", String::class.java)
        private val TONE = DSL.field("tone", String::class.java)
        private val TARGET_DURATION = DSL.field("target_duration", Int::class.java)
        private val ESTIMATED_WORD_COUNT = DSL.field("estimated_word_count", Int::class.java)
        private val KEYWORDS = DSL.field("keywords", Array<String>::class.java)
        private val TARGET_AUDIENCE = DSL.field("target_audience", String::class.java)
        private val NOTES = DSL.field("notes", String::class.java)
        private val CREDIT_COST = DSL.field("credit_cost", Int::class.java)
    }

    override fun findByUserId(userId: Long): List<Script> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toScript() }

    override fun findByUserIdAndStatus(userId: Long, status: String): List<Script> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .and(STATUS.eq(status))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toScript() }

    override fun findById(id: Long): Script? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toScript()

    override fun save(script: Script): Script {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, script.userId)
            .set(TITLE, script.title)
            .set(TOPIC, script.topic)
            .set(FORMAT, script.format)
            .set(TONE, script.tone)
            .set(STATUS, script.status)
            .set(TARGET_DURATION, script.targetDuration)
            .set(ESTIMATED_WORD_COUNT, script.estimatedWordCount)
            .set(KEYWORDS, script.keywords.toTypedArray())
            .set(TARGET_AUDIENCE, script.targetAudience)
            .set(NOTES, script.notes)
            .set(CREDIT_COST, script.creditCost)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun update(script: Script): Script {
        dsl.update(TABLE)
            .set(TITLE, script.title)
            .set(TOPIC, script.topic)
            .set(FORMAT, script.format)
            .set(TONE, script.tone)
            .set(STATUS, script.status)
            .set(TARGET_DURATION, script.targetDuration)
            .set(ESTIMATED_WORD_COUNT, script.estimatedWordCount)
            .set(KEYWORDS, script.keywords.toTypedArray())
            .set(TARGET_AUDIENCE, script.targetAudience)
            .set(NOTES, script.notes)
            .set(CREDIT_COST, script.creditCost)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(script.id))
            .execute()

        return findById(script.id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toScript(): Script {
        val keywordsRaw = get("keywords")
        val keywordsList: List<String> = when (keywordsRaw) {
            is Array<*> -> keywordsRaw.filterIsInstance<String>()
            else -> emptyList()
        }
        return Script(
            id = get(ID),
            userId = get(USER_ID),
            title = get(TITLE),
            topic = get(TOPIC),
            format = get(FORMAT),
            tone = get(TONE),
            status = get(STATUS),
            targetDuration = get(TARGET_DURATION),
            estimatedWordCount = get(ESTIMATED_WORD_COUNT),
            keywords = keywordsList,
            targetAudience = get(TARGET_AUDIENCE),
            notes = get(NOTES),
            creditCost = get(CREDIT_COST),
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
            updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
        )
    }
}

@Repository
class ScriptSectionJooqRepository(
    private val dsl: DSLContext,
) : ScriptSectionRepository {

    companion object {
        private val TABLE = DSL.table("script_sections")
        private val SCRIPT_ID = DSL.field("script_id", Long::class.java)
        private val TYPE = DSL.field("type", String::class.java)
        private val CONTENT = DSL.field("content", String::class.java)
        private val DURATION = DSL.field("duration", Int::class.java)
        private val NOTES = DSL.field("notes", String::class.java)
        private val ORDER_NUMBER = DSL.field("order_number", Int::class.java)
    }

    override fun findByScriptId(scriptId: Long): List<ScriptSection> =
        dsl.select().from(TABLE).where(SCRIPT_ID.eq(scriptId))
            .orderBy(ORDER_NUMBER.asc())
            .fetch().map { it.toSection() }

    override fun findById(id: Long): ScriptSection? =
        dsl.select().from(TABLE).where(Fields.ID.eq(id)).fetchOne()?.toSection()

    override fun save(section: ScriptSection): ScriptSection {
        val id = dsl.insertInto(TABLE)
            .set(SCRIPT_ID, section.scriptId)
            .set(TYPE, section.type)
            .set(Fields.TITLE, section.title)
            .set(CONTENT, section.content)
            .set(DURATION, section.duration)
            .set(NOTES, section.notes)
            .set(ORDER_NUMBER, section.orderNumber)
            .returningResult(Fields.ID)
            .fetchOne()!!.get(Fields.ID)

        return findById(id)!!
    }

    override fun update(section: ScriptSection): ScriptSection {
        dsl.update(TABLE)
            .set(TYPE, section.type)
            .set(Fields.TITLE, section.title)
            .set(CONTENT, section.content)
            .set(DURATION, section.duration)
            .set(NOTES, section.notes)
            .set(ORDER_NUMBER, section.orderNumber)
            .where(Fields.ID.eq(section.id))
            .execute()

        return findById(section.id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(Fields.ID.eq(id)).execute()
    }

    override fun deleteByScriptId(scriptId: Long) {
        dsl.deleteFrom(TABLE).where(SCRIPT_ID.eq(scriptId)).execute()
    }

    private fun Record.toSection(): ScriptSection =
        ScriptSection(
            id = get(Fields.ID),
            scriptId = get(SCRIPT_ID),
            type = get(TYPE),
            title = get(Fields.TITLE),
            content = get(CONTENT),
            duration = get(DURATION),
            notes = get(NOTES),
            orderNumber = get(ORDER_NUMBER),
            createdAt = localDateTime(Fields.CREATED_AT) ?: LocalDateTime.now(),
        )
}
