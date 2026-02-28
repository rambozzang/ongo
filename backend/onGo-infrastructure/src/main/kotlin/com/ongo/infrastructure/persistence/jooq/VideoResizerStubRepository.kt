package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.videoresizer.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class VideoResizerJooqRepository(
    private val dsl: DSLContext,
) : VideoResizerRepository {

    companion object {
        private val TABLE = DSL.table("resize_jobs")
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
        private val ORIGINAL_ASPECT_RATIO = DSL.field("original_aspect_ratio", String::class.java)
        private val TARGET_ASPECT_RATIO = DSL.field("target_aspect_ratio", String::class.java)
        private val PROGRESS = DSL.field("progress", Int::class.java)
        private val OUTPUT_URL = DSL.field("output_url", String::class.java)
        private val THUMBNAIL_URL = DSL.field("thumbnail_url", String::class.java)
        private val SMART_CROP = DSL.field("smart_crop", Boolean::class.java)
        private val FOCUS_POINT_X = DSL.field("focus_point_x", BigDecimal::class.java)
        private val FOCUS_POINT_Y = DSL.field("focus_point_y", BigDecimal::class.java)
        private val COMPLETED_AT = DSL.field("completed_at", LocalDateTime::class.java)
    }

    override fun findById(id: Long): ResizeJob? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toJob()

    override fun findByUserId(userId: Long): List<ResizeJob> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toJob() }

    override fun save(resizeJob: ResizeJob): ResizeJob {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, resizeJob.userId)
            .set(VIDEO_ID, resizeJob.videoId)
            .set(VIDEO_TITLE, resizeJob.videoTitle)
            .set(ORIGINAL_ASPECT_RATIO, resizeJob.originalAspectRatio)
            .set(TARGET_ASPECT_RATIO, resizeJob.targetAspectRatio)
            .set(PLATFORM, resizeJob.platform)
            .set(STATUS, resizeJob.status)
            .set(PROGRESS, resizeJob.progress)
            .set(OUTPUT_URL, resizeJob.outputUrl)
            .set(THUMBNAIL_URL, resizeJob.thumbnailUrl)
            .set(SMART_CROP, resizeJob.smartCrop)
            .set(FOCUS_POINT_X, resizeJob.focusPointX)
            .set(FOCUS_POINT_Y, resizeJob.focusPointY)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun update(resizeJob: ResizeJob): ResizeJob {
        dsl.update(TABLE)
            .set(VIDEO_TITLE, resizeJob.videoTitle)
            .set(ORIGINAL_ASPECT_RATIO, resizeJob.originalAspectRatio)
            .set(TARGET_ASPECT_RATIO, resizeJob.targetAspectRatio)
            .set(PLATFORM, resizeJob.platform)
            .set(STATUS, resizeJob.status)
            .set(PROGRESS, resizeJob.progress)
            .set(OUTPUT_URL, resizeJob.outputUrl)
            .set(THUMBNAIL_URL, resizeJob.thumbnailUrl)
            .set(SMART_CROP, resizeJob.smartCrop)
            .set(FOCUS_POINT_X, resizeJob.focusPointX)
            .set(FOCUS_POINT_Y, resizeJob.focusPointY)
            .set(COMPLETED_AT, resizeJob.completedAt)
            .where(ID.eq(resizeJob.id))
            .execute()

        return findById(resizeJob.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toJob(): ResizeJob =
        ResizeJob(
            id = get(ID),
            userId = get(USER_ID),
            videoId = get(VIDEO_ID),
            videoTitle = get(VIDEO_TITLE),
            originalAspectRatio = get(ORIGINAL_ASPECT_RATIO),
            targetAspectRatio = get(TARGET_ASPECT_RATIO),
            platform = get(PLATFORM),
            status = get(STATUS) ?: "PENDING",
            progress = get(PROGRESS) ?: 0,
            outputUrl = get(OUTPUT_URL),
            thumbnailUrl = get(THUMBNAIL_URL),
            smartCrop = get(SMART_CROP) ?: true,
            focusPointX = get(FOCUS_POINT_X),
            focusPointY = get(FOCUS_POINT_Y),
            createdAt = localDateTime(CREATED_AT),
            completedAt = localDateTime(COMPLETED_AT),
        )
}
