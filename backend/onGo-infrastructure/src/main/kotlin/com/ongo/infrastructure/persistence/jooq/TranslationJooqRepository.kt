package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.translation.TranslationRepository
import com.ongo.domain.translation.VideoTranslation
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.LANGUAGE
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.SUBTITLE_CONTENT
import com.ongo.infrastructure.persistence.jooq.Fields.TAGS_JSONB
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEO_TRANSLATIONS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class TranslationJooqRepository(
    private val dsl: DSLContext,
) : TranslationRepository {

    override fun findByVideoId(videoId: Long): List<VideoTranslation> =
        dsl.select().from(VIDEO_TRANSLATIONS).where(VIDEO_ID.eq(videoId))
            .orderBy(CREATED_AT.desc()).fetch().map { it.toTranslation() }

    override fun findById(id: Long): VideoTranslation? =
        dsl.select().from(VIDEO_TRANSLATIONS).where(ID.eq(id)).fetchOne()?.toTranslation()

    override fun findByVideoIdAndLanguage(videoId: Long, language: String): VideoTranslation? =
        dsl.select().from(VIDEO_TRANSLATIONS)
            .where(VIDEO_ID.eq(videoId)).and(LANGUAGE.eq(language))
            .fetchOne()?.toTranslation()

    override fun save(translation: VideoTranslation): VideoTranslation {
        val id = dsl.insertInto(VIDEO_TRANSLATIONS)
            .set(VIDEO_ID, translation.videoId)
            .set(LANGUAGE, translation.language)
            .set(TITLE, translation.title)
            .set(DESCRIPTION, translation.description)
            .set(TAGS_JSONB, translation.tags)
            .set(SUBTITLE_CONTENT, translation.subtitleContent)
            .set(STATUS, translation.status)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun update(id: Long, title: String?, description: String?, tags: String?, subtitleContent: String?, status: String?) {
        val sets = mutableMapOf<org.jooq.Field<*>, Any?>()
        if (title != null) sets[TITLE] = title
        if (description != null) sets[DESCRIPTION] = description
        if (tags != null) sets[TAGS_JSONB] = tags
        if (subtitleContent != null) sets[SUBTITLE_CONTENT] = subtitleContent
        if (status != null) sets[STATUS] = status
        sets[UPDATED_AT] = java.time.LocalDateTime.now()
        if (sets.size <= 1) return
        dsl.update(VIDEO_TRANSLATIONS).set(sets).where(ID.eq(id)).execute()
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(VIDEO_TRANSLATIONS).where(ID.eq(id)).execute()
    }

    private fun Record.toTranslation() = VideoTranslation(
        id = get(ID),
        videoId = get(VIDEO_ID),
        language = get(LANGUAGE),
        title = get(TITLE),
        description = get(DESCRIPTION),
        tags = get(TAGS_JSONB),
        subtitleContent = get(SUBTITLE_CONTENT),
        status = get(STATUS),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
