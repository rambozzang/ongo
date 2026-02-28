package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contenttranslator.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class TranslationGlossaryJooqRepository(
    private val dsl: DSLContext,
) : TranslationGlossaryRepository {

    companion object {
        private val TABLE = DSL.table("translation_glossary")
        private val SOURCE_WORD = DSL.field("source_word", String::class.java)
        private val TARGET_WORD = DSL.field("target_word", String::class.java)
        private val SOURCE_LANGUAGE = DSL.field("source_language", String::class.java)
        private val TARGET_LANGUAGE = DSL.field("target_language", String::class.java)
        private val CONTEXT = DSL.field("context", String::class.java)
    }

    override fun findByUserId(userId: Long): List<TranslationGlossary> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch().map { it.toTranslationGlossary() }

    override fun save(glossary: TranslationGlossary): TranslationGlossary {
        val newId = dsl.insertInto(TABLE)
            .set(USER_ID, glossary.userId)
            .set(SOURCE_WORD, glossary.sourceWord)
            .set(TARGET_WORD, glossary.targetWord)
            .set(SOURCE_LANGUAGE, glossary.sourceLanguage)
            .set(TARGET_LANGUAGE, glossary.targetLanguage)
            .set(CONTEXT, glossary.context)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return dsl.select().from(TABLE).where(ID.eq(newId)).fetchOne()!!.toTranslationGlossary()
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toTranslationGlossary(): TranslationGlossary =
        TranslationGlossary(
            id = get(ID),
            userId = get(USER_ID),
            sourceWord = get(SOURCE_WORD),
            targetWord = get(TARGET_WORD),
            sourceLanguage = get(SOURCE_LANGUAGE),
            targetLanguage = get(TARGET_LANGUAGE),
            context = get(CONTEXT),
            createdAt = localDateTime(CREATED_AT),
        )
}

@Repository
class TranslationJobJooqRepository(
    private val dsl: DSLContext,
) : TranslationJobRepository {

    companion object {
        private val TABLE = DSL.table("translation_jobs")
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
        private val SOURCE_LANGUAGE = DSL.field("source_language", String::class.java)
        private val TARGET_LANGUAGE = DSL.field("target_language", String::class.java)
        private val CONTENT_TYPE = DSL.field("content_type", String::class.java)
        private val ORIGINAL_TEXT = DSL.field("original_text", String::class.java)
        private val TRANSLATED_TEXT = DSL.field("translated_text", String::class.java)
        private val QUALITY = DSL.field("quality", Int::class.java)
        private val COMPLETED_AT = DSL.field("completed_at", LocalDateTime::class.java)
    }

    override fun findById(id: Long): TranslationJob? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toTranslationJob()

    override fun findByUserId(userId: Long): List<TranslationJob> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch().map { it.toTranslationJob() }

    override fun save(job: TranslationJob): TranslationJob {
        val newId = dsl.insertInto(TABLE)
            .set(USER_ID, job.userId)
            .set(VIDEO_ID, job.videoId)
            .set(VIDEO_TITLE, job.videoTitle)
            .set(SOURCE_LANGUAGE, job.sourceLanguage)
            .set(TARGET_LANGUAGE, job.targetLanguage)
            .set(CONTENT_TYPE, job.contentType)
            .set(ORIGINAL_TEXT, job.originalText)
            .set(TRANSLATED_TEXT, job.translatedText)
            .set(QUALITY, job.quality)
            .set(STATUS, job.status)
            .set(COMPLETED_AT, job.completedAt)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(newId)!!
    }

    override fun updateStatus(id: Long, status: String) {
        dsl.update(TABLE)
            .set(STATUS, status)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toTranslationJob(): TranslationJob =
        TranslationJob(
            id = get(ID),
            userId = get(USER_ID),
            videoId = get(VIDEO_ID),
            videoTitle = get(VIDEO_TITLE),
            sourceLanguage = get(SOURCE_LANGUAGE),
            targetLanguage = get(TARGET_LANGUAGE),
            contentType = get(CONTENT_TYPE) ?: "ALL",
            originalText = get(ORIGINAL_TEXT),
            translatedText = get(TRANSLATED_TEXT),
            quality = get(QUALITY),
            status = get(STATUS) ?: "PENDING",
            createdAt = localDateTime(CREATED_AT),
            completedAt = localDateTime(COMPLETED_AT),
        )
}
