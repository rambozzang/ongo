package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.subtitletranslation.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class SubtitleTranslationJooqRepository(
    private val dsl: DSLContext,
) : SubtitleTranslationRepository {

    companion object {
        private val TABLE = DSL.table("subtitle_translations")
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
        private val SOURCE_LANGUAGE = DSL.field("source_language", String::class.java)
        private val TARGET_LANGUAGE = DSL.field("target_language", String::class.java)
        private val PROGRESS = DSL.field("progress", Int::class.java)
        private val SOURCE_LINE_COUNT = DSL.field("source_line_count", Int::class.java)
        private val TRANSLATED_LINE_COUNT = DSL.field("translated_line_count", Int::class.java)
        private val QUALITY = DSL.field("quality", Int::class.java)
        private val COST = DSL.field("cost", Int::class.java)
        private val COMPLETED_AT = DSL.field("completed_at", LocalDateTime::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<SubtitleTranslation> =
        dsl.select().from(TABLE).where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toTranslation() }

    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<SubtitleTranslation> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(STATUS.eq(status))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toTranslation() }

    override fun findById(id: Long): SubtitleTranslation? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toTranslation()

    override fun save(translation: SubtitleTranslation): SubtitleTranslation {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, translation.workspaceId)
            .set(VIDEO_TITLE, translation.videoTitle)
            .set(SOURCE_LANGUAGE, translation.sourceLanguage)
            .set(TARGET_LANGUAGE, translation.targetLanguage)
            .set(STATUS, translation.status)
            .set(PROGRESS, translation.progress)
            .set(SOURCE_LINE_COUNT, translation.sourceLineCount)
            .set(TRANSLATED_LINE_COUNT, translation.translatedLineCount)
            .set(QUALITY, translation.quality)
            .set(COST, translation.cost)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun update(translation: SubtitleTranslation): SubtitleTranslation {
        dsl.update(TABLE)
            .set(VIDEO_TITLE, translation.videoTitle)
            .set(SOURCE_LANGUAGE, translation.sourceLanguage)
            .set(TARGET_LANGUAGE, translation.targetLanguage)
            .set(STATUS, translation.status)
            .set(PROGRESS, translation.progress)
            .set(SOURCE_LINE_COUNT, translation.sourceLineCount)
            .set(TRANSLATED_LINE_COUNT, translation.translatedLineCount)
            .set(QUALITY, translation.quality)
            .set(COST, translation.cost)
            .set(COMPLETED_AT, translation.completedAt)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(translation.id))
            .execute()

        return findById(translation.id)!!
    }

    private fun Record.toTranslation(): SubtitleTranslation =
        SubtitleTranslation(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            videoTitle = get(VIDEO_TITLE),
            sourceLanguage = get(SOURCE_LANGUAGE),
            targetLanguage = get(TARGET_LANGUAGE),
            status = get(STATUS) ?: "PENDING",
            progress = get(PROGRESS) ?: 0,
            sourceLineCount = get(SOURCE_LINE_COUNT) ?: 0,
            translatedLineCount = get(TRANSLATED_LINE_COUNT) ?: 0,
            quality = get(QUALITY) ?: 0,
            cost = get(COST) ?: 0,
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
            completedAt = localDateTime(COMPLETED_AT),
            updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
        )
}

@Repository
class SupportedLanguageJooqRepository(
    private val dsl: DSLContext,
) : SupportedLanguageRepository {

    companion object {
        private val TABLE = DSL.table("supported_languages")
        private val CODE = DSL.field("code", String::class.java)
        private val NAME = DSL.field("name", String::class.java)
        private val NATIVE_NAME = DSL.field("native_name", String::class.java)
        private val IS_ACTIVE = DSL.field("is_active", Boolean::class.java)
    }

    override fun findAll(): List<SupportedLanguage> =
        dsl.select().from(TABLE)
            .where(IS_ACTIVE.eq(true))
            .fetch().map { it.toLanguage() }

    override fun findByCode(code: String): SupportedLanguage? =
        dsl.select().from(TABLE).where(CODE.eq(code)).fetchOne()?.toLanguage()

    private fun Record.toLanguage(): SupportedLanguage =
        SupportedLanguage(
            id = get(Fields.ID),
            code = get(CODE),
            name = get(NAME),
            nativeName = get(NATIVE_NAME),
            isActive = get(IS_ACTIVE) ?: true,
            createdAt = localDateTime(Fields.CREATED_AT) ?: LocalDateTime.now(),
        )
}

@Repository
class TranslationLineJooqRepository(
    private val dsl: DSLContext,
) : TranslationLineRepository {

    companion object {
        private val TABLE = DSL.table("translation_lines")
        private val TRANSLATION_ID = DSL.field("translation_id", Long::class.java)
        private val LINE_NUMBER = DSL.field("line_number", Int::class.java)
        private val START_TIME = DSL.field("start_time", String::class.java)
        private val END_TIME = DSL.field("end_time", String::class.java)
        private val SOURCE_TEXT = DSL.field("source_text", String::class.java)
        private val TRANSLATED_TEXT = DSL.field("translated_text", String::class.java)
        private val IS_EDITED = DSL.field("is_edited", Boolean::class.java)
    }

    override fun findByTranslationId(translationId: Long): List<TranslationLine> =
        dsl.select().from(TABLE).where(TRANSLATION_ID.eq(translationId))
            .orderBy(LINE_NUMBER.asc())
            .fetch().map { it.toLine() }

    override fun findById(id: Long): TranslationLine? =
        dsl.select().from(TABLE).where(Fields.ID.eq(id)).fetchOne()?.toLine()

    override fun save(line: TranslationLine): TranslationLine {
        val id = dsl.insertInto(TABLE)
            .set(TRANSLATION_ID, line.translationId)
            .set(LINE_NUMBER, line.lineNumber)
            .set(START_TIME, line.startTime)
            .set(END_TIME, line.endTime)
            .set(SOURCE_TEXT, line.sourceText)
            .set(TRANSLATED_TEXT, line.translatedText)
            .set(IS_EDITED, line.isEdited)
            .returningResult(Fields.ID)
            .fetchOne()!!.get(Fields.ID)

        return findById(id)!!
    }

    override fun saveBatch(lines: List<TranslationLine>): List<TranslationLine> {
        if (lines.isEmpty()) return emptyList()

        val translationId = lines.first().translationId
        lines.forEach { line ->
            dsl.insertInto(TABLE)
                .set(TRANSLATION_ID, line.translationId)
                .set(LINE_NUMBER, line.lineNumber)
                .set(START_TIME, line.startTime)
                .set(END_TIME, line.endTime)
                .set(SOURCE_TEXT, line.sourceText)
                .set(TRANSLATED_TEXT, line.translatedText)
                .set(IS_EDITED, line.isEdited)
                .execute()
        }

        return findByTranslationId(translationId)
    }

    override fun update(line: TranslationLine): TranslationLine {
        dsl.update(TABLE)
            .set(TRANSLATED_TEXT, line.translatedText)
            .set(IS_EDITED, line.isEdited)
            .set(Fields.UPDATED_AT, LocalDateTime.now())
            .where(Fields.ID.eq(line.id))
            .execute()

        return findById(line.id)!!
    }

    private fun Record.toLine(): TranslationLine =
        TranslationLine(
            id = get(Fields.ID),
            translationId = get(TRANSLATION_ID),
            lineNumber = get(LINE_NUMBER),
            startTime = get(START_TIME),
            endTime = get(END_TIME),
            sourceText = get(SOURCE_TEXT),
            translatedText = get(TRANSLATED_TEXT) ?: "",
            isEdited = get(IS_EDITED) ?: false,
            createdAt = localDateTime(Fields.CREATED_AT) ?: LocalDateTime.now(),
            updatedAt = localDateTime(Fields.UPDATED_AT) ?: LocalDateTime.now(),
        )
}
