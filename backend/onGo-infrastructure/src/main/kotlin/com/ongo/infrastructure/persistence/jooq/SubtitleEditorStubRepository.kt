package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.subtitleeditor.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.LANGUAGE
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class SubtitleEditorJooqRepository(
    private val dsl: DSLContext,
) : SubtitleEditorRepository {

    companion object {
        private val TABLE = DSL.table("subtitle_tracks")
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
        private val CUES = DSL.field("cues", String::class.java)
        private val TOTAL_DURATION = DSL.field("total_duration", BigDecimal::class.java)
        private val WORD_COUNT = DSL.field("word_count", Int::class.java)
    }

    override fun findById(id: Long): SubtitleTrack? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toTrack()

    override fun findByUserId(userId: Long): List<SubtitleTrack> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toTrack() }

    override fun findByVideoId(videoId: Long): List<SubtitleTrack> =
        dsl.select().from(TABLE).where(VIDEO_ID.eq(videoId))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toTrack() }

    override fun save(subtitleTrack: SubtitleTrack): SubtitleTrack {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, subtitleTrack.userId)
            .set(VIDEO_ID, subtitleTrack.videoId)
            .set(VIDEO_TITLE, subtitleTrack.videoTitle)
            .set(LANGUAGE, subtitleTrack.language)
            .set(STATUS, subtitleTrack.status)
            .set(CUES, DSL.field("?::jsonb", String::class.java, subtitleTrack.cues))
            .set(TOTAL_DURATION, subtitleTrack.totalDuration)
            .set(WORD_COUNT, subtitleTrack.wordCount)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun update(subtitleTrack: SubtitleTrack): SubtitleTrack {
        dsl.update(TABLE)
            .set(VIDEO_TITLE, subtitleTrack.videoTitle)
            .set(LANGUAGE, subtitleTrack.language)
            .set(STATUS, subtitleTrack.status)
            .set(CUES, DSL.field("?::jsonb", String::class.java, subtitleTrack.cues))
            .set(TOTAL_DURATION, subtitleTrack.totalDuration)
            .set(WORD_COUNT, subtitleTrack.wordCount)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(subtitleTrack.id))
            .execute()

        return findById(subtitleTrack.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toTrack(): SubtitleTrack {
        val cuesRaw = get("cues")
        return SubtitleTrack(
            id = get(ID),
            userId = get(USER_ID),
            videoId = get(VIDEO_ID),
            videoTitle = get(VIDEO_TITLE),
            language = get(LANGUAGE),
            status = get(STATUS) ?: "DRAFT",
            cues = when (cuesRaw) {
                is String -> cuesRaw
                else -> cuesRaw?.toString() ?: "[]"
            },
            totalDuration = get(TOTAL_DURATION) ?: BigDecimal.ZERO,
            wordCount = get(WORD_COUNT) ?: 0,
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
