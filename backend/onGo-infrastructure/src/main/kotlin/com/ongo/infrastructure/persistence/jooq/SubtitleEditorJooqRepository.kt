package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.subtitleeditor.SubtitleEditorRepository
import com.ongo.domain.subtitleeditor.SubtitleTrack
import com.ongo.infrastructure.persistence.jooq.Fields.CUES
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.LANGUAGE
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TOTAL_DURATION
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.WORD_COUNT
import com.ongo.infrastructure.persistence.jooq.Tables.SUBTITLE_TRACKS
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class SubtitleEditorJooqRepository(
    private val dsl: DSLContext,
) : SubtitleEditorRepository {

    override fun findById(id: Long): SubtitleTrack? =
        dsl.select()
            .from(SUBTITLE_TRACKS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toSubtitleTrack()

    override fun findByUserId(userId: Long): List<SubtitleTrack> =
        dsl.select()
            .from(SUBTITLE_TRACKS)
            .where(USER_ID.eq(userId))
            .orderBy(UPDATED_AT.desc(), ID.desc())
            .fetch()
            .map { it.toSubtitleTrack() }

    override fun findByVideoId(videoId: Long): List<SubtitleTrack> =
        dsl.select()
            .from(SUBTITLE_TRACKS)
            .where(VIDEO_ID.eq(videoId))
            .orderBy(LANGUAGE.asc(), ID.asc())
            .fetch()
            .map { it.toSubtitleTrack() }

    override fun save(subtitleTrack: SubtitleTrack): SubtitleTrack {
        val id = dsl.insertInto(SUBTITLE_TRACKS)
            .set(USER_ID, subtitleTrack.userId)
            .set(VIDEO_ID, subtitleTrack.videoId)
            .set(VIDEO_TITLE, subtitleTrack.videoTitle)
            .set(LANGUAGE, subtitleTrack.language)
            .set(STATUS, subtitleTrack.status)
            .set(CUES, JSONB.jsonb(subtitleTrack.cues))
            .set(TOTAL_DURATION, subtitleTrack.totalDuration)
            .set(WORD_COUNT, subtitleTrack.wordCount)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(subtitleTrack: SubtitleTrack): SubtitleTrack {
        val id = requireNotNull(subtitleTrack.id) { "자막 트랙 ID가 없습니다" }
        val affected = dsl.update(SUBTITLE_TRACKS)
            .set(LANGUAGE, subtitleTrack.language)
            .set(STATUS, subtitleTrack.status)
            .set(CUES, JSONB.jsonb(subtitleTrack.cues))
            .set(TOTAL_DURATION, subtitleTrack.totalDuration)
            .set(WORD_COUNT, subtitleTrack.wordCount)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id))
            .execute()

        if (affected == 0) throw IllegalStateException("자막 트랙을 수정할 수 없습니다")
        return findById(id)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(SUBTITLE_TRACKS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toSubtitleTrack(): SubtitleTrack {
        val rawCues = get(CUES)
        val cues = rawCues?.data() ?: "[]"
        return SubtitleTrack(
            id = get(ID),
            userId = get(USER_ID),
            videoId = get(VIDEO_ID),
            videoTitle = get(VIDEO_TITLE),
            language = get(LANGUAGE),
            status = get(STATUS) ?: "DRAFT",
            cues = cues,
            totalDuration = get(TOTAL_DURATION) ?: java.math.BigDecimal.ZERO,
            wordCount = get(WORD_COUNT) ?: 0,
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
