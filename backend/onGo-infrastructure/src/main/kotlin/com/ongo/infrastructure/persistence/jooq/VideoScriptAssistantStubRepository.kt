package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.videoscriptassistant.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class VideoScriptJooqRepository(
    private val dsl: DSLContext,
) : VideoScriptRepository {

    companion object {
        private val TABLE = DSL.table("video_scripts")
        private val TITLE = DSL.field("title", String::class.java)
        private val VIDEO_ID = DSL.field("video_id", Long::class.java)
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
        private val CONTENT = DSL.field("content", String::class.java)
        private val TONE = DSL.field("tone", String::class.java)
        private val TARGET_LENGTH = DSL.field("target_length", Int::class.java)
        private val WORD_COUNT = DSL.field("word_count", Int::class.java)
        private val HOOK_LINE = DSL.field("hook_line", String::class.java)
        private val CTA_TEXT = DSL.field("cta_text", String::class.java)
    }

    override fun findById(id: Long): VideoScript? =
        dsl.select().from(TABLE)
            .where(ID.eq(id))
            .fetchOne()?.toScript()

    override fun findByUserId(userId: Long): List<VideoScript> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .fetch { it.toScript() }

    override fun save(script: VideoScript): VideoScript {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, script.userId)
            .set(TITLE, script.title)
            .set(VIDEO_ID, script.videoId)
            .set(VIDEO_TITLE, script.videoTitle)
            .set(CONTENT, script.content)
            .set(TONE, script.tone)
            .set(TARGET_LENGTH, script.targetLength)
            .set(WORD_COUNT, script.wordCount)
            .set(HOOK_LINE, script.hookLine)
            .set(CTA_TEXT, script.ctaText)
            .set(STATUS, script.status)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: String) {
        dsl.update(TABLE)
            .set(STATUS, status)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id))
            .execute()
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toScript(): VideoScript =
        VideoScript(
            id = get(ID),
            userId = get(USER_ID),
            title = get(TITLE),
            videoId = get("video_id")?.let { (it as Number).toLong() },
            videoTitle = get(VIDEO_TITLE),
            content = get(CONTENT) ?: "",
            tone = get(TONE) ?: "CASUAL",
            targetLength = get(TARGET_LENGTH) ?: 500,
            wordCount = get(WORD_COUNT) ?: 0,
            hookLine = get(HOOK_LINE),
            ctaText = get(CTA_TEXT),
            status = get(STATUS) ?: "DRAFT",
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
}

@Repository
class ScriptSuggestionJooqRepository(
    private val dsl: DSLContext,
) : ScriptSuggestionRepository {

    companion object {
        private val TABLE = DSL.table("script_suggestions")
        private val SCRIPT_ID = DSL.field("script_id", Long::class.java)
        private val SECTION_TYPE = DSL.field("section_type", String::class.java)
        private val ORIGINAL_TEXT = DSL.field("original_text", String::class.java)
        private val SUGGESTED_TEXT = DSL.field("suggested_text", String::class.java)
        private val REASON = DSL.field("reason", String::class.java)
        private val IS_APPLIED = DSL.field("is_applied", Boolean::class.java)
    }

    override fun findByScriptId(scriptId: Long): List<ScriptSuggestion> =
        dsl.select().from(TABLE)
            .where(SCRIPT_ID.eq(scriptId))
            .fetch { it.toSuggestion() }

    override fun save(suggestion: ScriptSuggestion): ScriptSuggestion {
        val id = dsl.insertInto(TABLE)
            .set(SCRIPT_ID, suggestion.scriptId)
            .set(SECTION_TYPE, suggestion.sectionType)
            .set(ORIGINAL_TEXT, suggestion.originalText)
            .set(SUGGESTED_TEXT, suggestion.suggestedText)
            .set(REASON, suggestion.reason)
            .set(IS_APPLIED, suggestion.isApplied)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toSuggestion()
    }

    override fun updateApplied(id: Long, isApplied: Boolean) {
        dsl.update(TABLE)
            .set(IS_APPLIED, isApplied)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toSuggestion(): ScriptSuggestion =
        ScriptSuggestion(
            id = get(ID),
            scriptId = get(SCRIPT_ID),
            sectionType = get(SECTION_TYPE),
            originalText = get(ORIGINAL_TEXT),
            suggestedText = get(SUGGESTED_TEXT),
            reason = get(REASON),
            isApplied = get(IS_APPLIED) ?: false,
            createdAt = localDateTime(CREATED_AT),
        )
}
