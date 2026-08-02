package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.ShortsClip
import com.ongo.domain.ugc.shorts.ShortsClipRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CAPTION
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DEDUP_KEY
import com.ongo.infrastructure.persistence.jooq.Fields.END_MS
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.RENDERED_VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.RUN_ID
import com.ongo.infrastructure.persistence.jooq.Fields.SCHEDULED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.SEQ
import com.ongo.infrastructure.persistence.jooq.Fields.START_MS
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SHORTS_CLIPS
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class ShortsClipJooqRepository(
    private val dsl: DSLContext,
) : ShortsClipRepository {

    override fun saveAll(clips: List<ShortsClip>): List<ShortsClip> =
        clips.map { clip ->
            val id = dsl.insertInto(UGC_SHORTS_CLIPS)
                .set(RUN_ID, clip.runId)
                .set(SEQ, clip.seq)
                .set(START_MS, clip.startMs)
                .set(END_MS, clip.endMs)
                .set(TITLE, clip.title)
                .set(CAPTION, clip.caption)
                .set(SUBTITLE_JSON_JSONB, clip.subtitleJson?.let { JSONB.jsonb(it) })
                .set(CROP_JSON_JSONB, clip.cropJson?.let { JSONB.jsonb(it) })
                .set(RENDER_SPEC_JSONB, clip.renderSpec?.let { JSONB.jsonb(it) })
                .set(STATUS, clip.status.name)
                .set(DEDUP_KEY, clip.dedupKey)
                .set(RENDERED_VIDEO_ID, clip.renderedVideoId)
                .set(SCHEDULED_AT, clip.scheduledAt?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) })
                .returningResult(ID)
                .fetchOne()!!
                .get(ID)
            findById(id)!!
        }

    override fun update(clip: ShortsClip): ShortsClip {
        dsl.update(UGC_SHORTS_CLIPS)
            .set(SEQ, clip.seq)
            .set(START_MS, clip.startMs)
            .set(END_MS, clip.endMs)
            .set(TITLE, clip.title)
            .set(CAPTION, clip.caption)
            .set(SUBTITLE_JSON_JSONB, clip.subtitleJson?.let { JSONB.jsonb(it) })
            .set(CROP_JSON_JSONB, clip.cropJson?.let { JSONB.jsonb(it) })
            .set(RENDER_SPEC_JSONB, clip.renderSpec?.let { JSONB.jsonb(it) })
            .set(STATUS, clip.status.name)
            .set(DEDUP_KEY, clip.dedupKey)
            .set(RENDERED_VIDEO_ID, clip.renderedVideoId)
            .set(SCHEDULED_AT, clip.scheduledAt?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) })
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(clip.id))
            .execute()

        return findById(clip.id)!!
    }

    override fun findByRunId(runId: Long): List<ShortsClip> =
        dsl.select()
            .from(UGC_SHORTS_CLIPS)
            .where(RUN_ID.eq(runId))
            .orderBy(SEQ.asc())
            .fetch()
            .map { it.toShortsClip() }

    override fun findById(id: Long): ShortsClip? =
        dsl.select()
            .from(UGC_SHORTS_CLIPS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toShortsClip()

    override fun deleteByRunId(runId: Long): Int =
        dsl.deleteFrom(UGC_SHORTS_CLIPS)
            .where(RUN_ID.eq(runId))
            .execute()

    private fun Record.toShortsClip(): ShortsClip = ShortsClip(
        id = get(ID),
        runId = get(RUN_ID),
        seq = get(SEQ),
        startMs = get(START_MS),
        endMs = get(END_MS),
        title = get(TITLE),
        caption = get(CAPTION),
        subtitleJson = jsonbString("subtitle_json"),
        cropJson = jsonbString("crop_json"),
        renderSpec = jsonbString("render_spec"),
        status = ClipStatus.valueOf(get(STATUS)),
        dedupKey = get(DEDUP_KEY),
        renderedVideoId = get(RENDERED_VIDEO_ID),
        scheduledAt = localDateTime(SCHEDULED_AT)?.atZone(ZoneOffset.UTC)?.toInstant(),
        createdAt = localDateTime(CREATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
        updatedAt = localDateTime(UPDATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
    )

    companion object {
        // subtitle_json/crop_json/render_spec은 JSONB 컬럼이라 String 바인딩으로 쓸 수 없어 JSONB 타입 필드로만 쓴다.
        private val SUBTITLE_JSON_JSONB = DSL.field("subtitle_json", JSONB::class.java)
        private val CROP_JSON_JSONB = DSL.field("crop_json", JSONB::class.java)
        private val RENDER_SPEC_JSONB = DSL.field("render_spec", JSONB::class.java)

        /** jsonb 컬럼 값을 JSON 문자열로 꺼낸다. 드라이버 반환 타입(JSONB/PGobject/String)을 모두 수용한다. */
        private fun Record.jsonbString(column: String): String? = when (val raw = get(column)) {
            null -> null
            is JSONB -> raw.data()
            is String -> raw
            else -> raw.toString()
        }
    }
}
