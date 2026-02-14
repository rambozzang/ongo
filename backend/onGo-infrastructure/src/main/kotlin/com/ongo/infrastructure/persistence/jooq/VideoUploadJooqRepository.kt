package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ERROR_MESSAGE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM_URL
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM_VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.PUBLISHED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEO_UPLOADS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class VideoUploadJooqRepository(
    private val dsl: DSLContext,
) : VideoUploadRepository {

    override fun findById(id: Long): VideoUpload? =
        dsl.select()
            .from(VIDEO_UPLOADS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toVideoUpload()

    override fun findByVideoId(videoId: Long): List<VideoUpload> =
        dsl.select()
            .from(VIDEO_UPLOADS)
            .where(VIDEO_ID.eq(videoId))
            .orderBy(CREATED_AT.asc())
            .fetch()
            .map { it.toVideoUpload() }

    override fun findByVideoIds(videoIds: List<Long>): Map<Long, List<VideoUpload>> {
        if (videoIds.isEmpty()) return emptyMap()
        return dsl.select()
            .from(VIDEO_UPLOADS)
            .where(VIDEO_ID.`in`(videoIds))
            .orderBy(CREATED_AT.asc())
            .fetch()
            .map { it.toVideoUpload() }
            .groupBy { it.videoId }
    }

    override fun findByVideoIdAndPlatform(videoId: Long, platform: Platform): VideoUpload? =
        dsl.select()
            .from(VIDEO_UPLOADS)
            .where(VIDEO_ID.eq(videoId))
            .and(PLATFORM_TEXT.eq(platform.name))
            .fetchOne()
            ?.toVideoUpload()

    override fun findByPlatformAndUserId(platform: Platform, userId: Long): List<VideoUpload> =
        dsl.select(
            DSL.field("video_uploads.id", Long::class.java).`as`("id"),
            DSL.field("video_uploads.video_id", Long::class.java).`as`("video_id"),
            DSL.field("video_uploads.platform", String::class.java).`as`("platform"),
            DSL.field("video_uploads.platform_video_id", String::class.java).`as`("platform_video_id"),
            DSL.field("video_uploads.status", String::class.java).`as`("status"),
            DSL.field("video_uploads.error_message", String::class.java).`as`("error_message"),
            DSL.field("video_uploads.platform_url", String::class.java).`as`("platform_url"),
            DSL.field("video_uploads.published_at", java.time.LocalDateTime::class.java).`as`("published_at"),
            DSL.field("video_uploads.created_at", java.time.LocalDateTime::class.java).`as`("created_at"),
            DSL.field("video_uploads.updated_at", java.time.LocalDateTime::class.java).`as`("updated_at")
        )
            .from(VIDEO_UPLOADS)
            .join(Tables.VIDEOS).on(
                DSL.field("video_uploads.video_id", Long::class.java)
                    .eq(DSL.field("videos.id", Long::class.java))
            )
            .where(DSL.field("video_uploads.platform", String::class.java).eq(platform.name))
            .and(DSL.field("videos.user_id", Long::class.java).eq(userId))
            .orderBy(DSL.field("video_uploads.created_at"))
            .fetch()
            .map { it.toVideoUpload() }

    override fun save(upload: VideoUpload): VideoUpload {
        val id = dsl.insertInto(VIDEO_UPLOADS)
            .set(VIDEO_ID, upload.videoId)
            .set(PLATFORM, upload.platform.name)
            .set(PLATFORM_VIDEO_ID, upload.platformVideoId)
            .set(STATUS, upload.status.name)
            .set(ERROR_MESSAGE, upload.errorMessage)
            .set(PLATFORM_URL, upload.platformUrl)
            .set(PUBLISHED_AT, upload.publishedAt)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(upload: VideoUpload): VideoUpload {
        dsl.update(VIDEO_UPLOADS)
            .set(PLATFORM_VIDEO_ID, upload.platformVideoId)
            .set(STATUS, upload.status.name)
            .set(ERROR_MESSAGE, upload.errorMessage)
            .set(PLATFORM_URL, upload.platformUrl)
            .set(PUBLISHED_AT, upload.publishedAt)
            .where(ID.eq(upload.id))
            .execute()

        return findById(upload.id!!)!!
    }

    override fun findByUserId(userId: Long): List<VideoUpload> =
        dsl.select(
            DSL.field("video_uploads.id", Long::class.java).`as`("id"),
            DSL.field("video_uploads.video_id", Long::class.java).`as`("video_id"),
            DSL.field("video_uploads.platform", String::class.java).`as`("platform"),
            DSL.field("video_uploads.platform_video_id", String::class.java).`as`("platform_video_id"),
            DSL.field("video_uploads.status", String::class.java).`as`("status"),
            DSL.field("video_uploads.error_message", String::class.java).`as`("error_message"),
            DSL.field("video_uploads.platform_url", String::class.java).`as`("platform_url"),
            DSL.field("video_uploads.published_at", java.time.LocalDateTime::class.java).`as`("published_at"),
            DSL.field("video_uploads.created_at", java.time.LocalDateTime::class.java).`as`("created_at"),
            DSL.field("video_uploads.updated_at", java.time.LocalDateTime::class.java).`as`("updated_at")
        )
            .from(VIDEO_UPLOADS)
            .join(Tables.VIDEOS).on(
                DSL.field("video_uploads.video_id", Long::class.java)
                    .eq(DSL.field("videos.id", Long::class.java))
            )
            .where(DSL.field("videos.user_id", Long::class.java).eq(userId))
            .orderBy(DSL.field("video_uploads.created_at"))
            .fetch()
            .map { it.toVideoUpload() }

    override fun findPendingUploads(): List<VideoUpload> =
        dsl.select()
            .from(VIDEO_UPLOADS)
            .where(STATUS.`in`(UploadStatus.UPLOADING.name, UploadStatus.PROCESSING.name))
            .orderBy(CREATED_AT.asc())
            .fetch()
            .map { it.toVideoUpload() }

    private fun Record.toVideoUpload(): VideoUpload {
        val platformStr = get(PLATFORM) ?: "YOUTUBE"
        val statusStr = get(STATUS) ?: "DRAFT"
        return VideoUpload(
            id = get(ID),
            videoId = get(VIDEO_ID),
            platform = try { Platform.valueOf(platformStr) } catch (_: Exception) { Platform.YOUTUBE },
            platformVideoId = get(PLATFORM_VIDEO_ID),
            status = try { UploadStatus.valueOf(statusStr) } catch (_: Exception) { UploadStatus.DRAFT },
            errorMessage = get(ERROR_MESSAGE),
            platformUrl = get(PLATFORM_URL),
            publishedAt = localDateTime(PUBLISHED_AT),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
