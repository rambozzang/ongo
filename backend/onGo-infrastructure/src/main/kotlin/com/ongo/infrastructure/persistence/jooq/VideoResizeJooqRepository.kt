package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.video.VideoResizeRepository
import com.ongo.domain.video.entity.VideoResize
import com.ongo.infrastructure.persistence.jooq.Fields.ASPECT_RATIO
import com.ongo.infrastructure.persistence.jooq.Fields.COMPLETED_AT_RESIZE
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ERROR_MESSAGE
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_SIZE_BYTES
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.HEIGHT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.ORIGINAL_VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.WIDTH
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEO_RESIZES
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class VideoResizeJooqRepository(
    private val dsl: DSLContext,
) : VideoResizeRepository {

    override fun findById(id: Long): VideoResize? =
        dsl.select().from(VIDEO_RESIZES).where(ID.eq(id)).fetchOne()?.toVideoResize()

    override fun findByOriginalVideoId(videoId: Long): List<VideoResize> =
        dsl.select().from(VIDEO_RESIZES).where(ORIGINAL_VIDEO_ID.eq(videoId))
            .orderBy(CREATED_AT.desc()).fetch().map { it.toVideoResize() }

    override fun findByUserId(userId: Long): List<VideoResize> =
        dsl.select().from(VIDEO_RESIZES).where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc()).fetch().map { it.toVideoResize() }

    override fun save(resize: VideoResize): VideoResize {
        val id = dsl.insertInto(VIDEO_RESIZES)
            .set(USER_ID, resize.userId)
            .set(ORIGINAL_VIDEO_ID, resize.originalVideoId)
            .set(ASPECT_RATIO, resize.aspectRatio)
            .set(WIDTH, resize.width)
            .set(HEIGHT, resize.height)
            .set(STATUS, resize.status)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: String, fileUrl: String?, fileSizeBytes: Long?, errorMessage: String?) {
        val update = dsl.update(VIDEO_RESIZES)
            .set(STATUS, status)
        if (fileUrl != null) update.set(FILE_URL, fileUrl)
        if (fileSizeBytes != null) update.set(FILE_SIZE_BYTES, fileSizeBytes)
        if (errorMessage != null) update.set(ERROR_MESSAGE, errorMessage)
        if (status == "COMPLETED" || status == "FAILED") {
            update.set(COMPLETED_AT_RESIZE, LocalDateTime.now())
        }
        update.where(ID.eq(id)).execute()
    }

    private fun Record.toVideoResize(): VideoResize = VideoResize(
        id = get(ID),
        userId = get(USER_ID),
        originalVideoId = get(ORIGINAL_VIDEO_ID),
        aspectRatio = get(ASPECT_RATIO),
        width = get(WIDTH),
        height = get(HEIGHT),
        fileUrl = get(FILE_URL),
        fileSizeBytes = get(FILE_SIZE_BYTES),
        status = get(STATUS),
        errorMessage = get(ERROR_MESSAGE),
        createdAt = localDateTime(CREATED_AT),
        completedAt = localDateTime(COMPLETED_AT_RESIZE),
    )
}
