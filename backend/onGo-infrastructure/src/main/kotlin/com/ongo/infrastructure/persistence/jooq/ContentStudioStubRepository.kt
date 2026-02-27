package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentstudio.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ContentStudioJooqRepository(
    private val dsl: DSLContext,
) : ContentStudioRepository {

    companion object {
        // content_clips
        private val CLIPS_TABLE = DSL.table("content_clips")
        private val SOURCE_VIDEO_ID = DSL.field("source_video_id", Long::class.java)
        private val START_TIME_MS = DSL.field("start_time_ms", Long::class.java)
        private val END_TIME_MS = DSL.field("end_time_ms", Long::class.java)
        private val ASPECT_RATIO = DSL.field("aspect_ratio", String::class.java)
        private val OUTPUT_URL = DSL.field("output_url", String::class.java)

        // video_captions
        private val CAPTIONS_TABLE = DSL.table("video_captions")
        private val LANGUAGE = DSL.field("language", String::class.java)
        private val CAPTION_DATA = DSL.field("caption_data", String::class.java)

        // ai_thumbnails
        private val THUMBNAILS_TABLE = DSL.table("ai_thumbnails")
        private val STYLE = DSL.field("style", String::class.java)
        private val TEXT_OVERLAY = DSL.field("text_overlay", String::class.java)
        private val IMAGE_URL = DSL.field("image_url", String::class.java)
    }

    // --- Content Clips ---

    override fun findClipById(id: Long): ContentClip? =
        dsl.select()
            .from(CLIPS_TABLE)
            .where(ID.eq(id))
            .fetchOne()
            ?.toClip()

    override fun findClipsByUserId(userId: Long): List<ContentClip> =
        dsl.select()
            .from(CLIPS_TABLE)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toClip() }

    override fun saveClip(clip: ContentClip): ContentClip {
        val id = dsl.insertInto(CLIPS_TABLE)
            .set(USER_ID, clip.userId)
            .set(SOURCE_VIDEO_ID, clip.sourceVideoId)
            .set(TITLE, clip.title)
            .set(START_TIME_MS, clip.startTimeMs)
            .set(END_TIME_MS, clip.endTimeMs)
            .set(ASPECT_RATIO, clip.aspectRatio)
            .set(STATUS, clip.status)
            .set(OUTPUT_URL, clip.outputUrl)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findClipById(id)!!
    }

    override fun updateClip(clip: ContentClip): ContentClip {
        dsl.update(CLIPS_TABLE)
            .set(TITLE, clip.title)
            .set(START_TIME_MS, clip.startTimeMs)
            .set(END_TIME_MS, clip.endTimeMs)
            .set(ASPECT_RATIO, clip.aspectRatio)
            .set(STATUS, clip.status)
            .set(OUTPUT_URL, clip.outputUrl)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(clip.id))
            .execute()

        return findClipById(clip.id!!)!!
    }

    override fun deleteClip(id: Long) {
        dsl.deleteFrom(CLIPS_TABLE)
            .where(ID.eq(id))
            .execute()
    }

    // --- Video Captions ---

    override fun findCaptionById(id: Long): VideoCaption? =
        dsl.select()
            .from(CAPTIONS_TABLE)
            .where(ID.eq(id))
            .fetchOne()
            ?.toCaption()

    override fun findCaptionsByVideoId(videoId: Long): List<VideoCaption> =
        dsl.select()
            .from(CAPTIONS_TABLE)
            .where(VIDEO_ID.eq(videoId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toCaption() }

    override fun saveCaption(caption: VideoCaption): VideoCaption {
        val id = dsl.insertInto(CAPTIONS_TABLE)
            .set(VIDEO_ID, caption.videoId)
            .set(LANGUAGE, caption.language)
            .set(CAPTION_DATA, DSL.field("?::jsonb", String::class.java, caption.captionData))
            .set(STATUS, caption.status)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findCaptionById(id)!!
    }

    override fun updateCaption(caption: VideoCaption): VideoCaption {
        dsl.update(CAPTIONS_TABLE)
            .set(LANGUAGE, caption.language)
            .set(CAPTION_DATA, DSL.field("?::jsonb", String::class.java, caption.captionData))
            .set(STATUS, caption.status)
            .where(ID.eq(caption.id))
            .execute()

        return findCaptionById(caption.id!!)!!
    }

    override fun deleteCaption(id: Long) {
        dsl.deleteFrom(CAPTIONS_TABLE)
            .where(ID.eq(id))
            .execute()
    }

    // --- AI Thumbnails ---

    override fun findThumbnailById(id: Long): AiThumbnail? =
        dsl.select()
            .from(THUMBNAILS_TABLE)
            .where(ID.eq(id))
            .fetchOne()
            ?.toThumbnail()

    override fun findThumbnailsByVideoId(videoId: Long): List<AiThumbnail> =
        dsl.select()
            .from(THUMBNAILS_TABLE)
            .where(VIDEO_ID.eq(videoId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toThumbnail() }

    override fun saveThumbnail(thumbnail: AiThumbnail): AiThumbnail {
        val id = dsl.insertInto(THUMBNAILS_TABLE)
            .set(VIDEO_ID, thumbnail.videoId)
            .set(STYLE, thumbnail.style)
            .set(TEXT_OVERLAY, thumbnail.textOverlay)
            .set(IMAGE_URL, thumbnail.imageUrl)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findThumbnailById(id)!!
    }

    override fun deleteThumbnail(id: Long) {
        dsl.deleteFrom(THUMBNAILS_TABLE)
            .where(ID.eq(id))
            .execute()
    }

    // --- Record mapping ---

    private fun Record.toClip(): ContentClip = ContentClip(
        id = get(ID),
        userId = get(USER_ID),
        sourceVideoId = get(SOURCE_VIDEO_ID),
        title = get(TITLE),
        startTimeMs = toLong(get("start_time_ms")),
        endTimeMs = toLong(get("end_time_ms")),
        aspectRatio = get(ASPECT_RATIO) ?: "9:16",
        status = get(STATUS) ?: "PENDING",
        outputUrl = get(OUTPUT_URL),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )

    private fun Record.toCaption(): VideoCaption {
        val captionRaw = get("caption_data")
        return VideoCaption(
            id = get(ID),
            videoId = get(VIDEO_ID),
            language = get(LANGUAGE) ?: "ko",
            captionData = when (captionRaw) {
                is String -> captionRaw
                else -> captionRaw?.toString() ?: "[]"
            },
            status = get(STATUS) ?: "DRAFT",
            createdAt = localDateTime(CREATED_AT),
        )
    }

    private fun Record.toThumbnail(): AiThumbnail = AiThumbnail(
        id = get(ID),
        videoId = get(VIDEO_ID),
        style = get(STYLE),
        textOverlay = get(TEXT_OVERLAY),
        imageUrl = get(IMAGE_URL),
        createdAt = localDateTime(CREATED_AT),
    )

    private fun toLong(value: Any?): Long = when (value) {
        is Long -> value
        is Number -> value.toLong()
        else -> 0L
    }
}
