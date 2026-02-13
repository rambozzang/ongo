package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CATEGORY
import com.ongo.infrastructure.persistence.jooq.Fields.CONTENT_HASH
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.DURATION_SECONDS
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_SIZE_BYTES
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.ORIGINAL_FILENAME
import com.ongo.infrastructure.persistence.jooq.Fields.RESOLUTION
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.TAGS
import com.ongo.infrastructure.persistence.jooq.Fields.THUMBNAIL_URLS
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEOS
import com.fasterxml.jackson.databind.ObjectMapper
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.YearMonth

@Repository
class VideoJooqRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) : VideoRepository {

    override fun findById(id: Long): Video? =
        dsl.select()
            .from(VIDEOS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toVideo()

    override fun findByUserId(userId: Long, page: Int, size: Int, status: UploadStatus?): List<Video> {
        var query = dsl.select()
            .from(VIDEOS)
            .where(USER_ID.eq(userId))

        if (status != null) {
            query = query.and(STATUS_TEXT.eq(status.name))
        }

        return query
            .orderBy(CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toVideo() }
    }

    override fun countByUserId(userId: Long, status: UploadStatus?): Long {
        var condition = USER_ID.eq(userId)
        if (status != null) {
            condition = condition.and(STATUS_TEXT.eq(status.name))
        }

        return dsl.selectCount()
            .from(VIDEOS)
            .where(condition)
            .fetchOne(0, Long::class.java) ?: 0L
    }

    override fun countByUserIdAndMonth(userId: Long, yearMonth: YearMonth): Long {
        val startOfMonth = yearMonth.atDay(1).atStartOfDay()
        val startOfNextMonth = yearMonth.plusMonths(1).atDay(1).atStartOfDay()

        return dsl.selectCount()
            .from(VIDEOS)
            .where(USER_ID.eq(userId))
            .and(CREATED_AT.greaterOrEqual(startOfMonth))
            .and(CREATED_AT.lessThan(startOfNextMonth))
            .fetchOne(0, Long::class.java) ?: 0L
    }

    override fun save(video: Video): Video {
        val tagsArray = video.tags.toTypedArray()
        val thumbnailJson = JSONB.jsonb(objectMapper.writeValueAsString(video.thumbnailUrls))

        val record = dsl.insertInto(VIDEOS)
            .set(USER_ID, video.userId)
            .set(TITLE, video.title)
            .set(DESCRIPTION, video.description)
            .set(DSL.field("tags", Array<String>::class.java), tagsArray)
            .set(CATEGORY, video.category)
            .set(FILE_URL, video.fileUrl)
            .set(FILE_SIZE_BYTES, video.fileSizeBytes)
            .set(DURATION_SECONDS, video.durationSeconds)
            .set(RESOLUTION, video.resolution)
            .set(ORIGINAL_FILENAME, video.originalFilename)
            .set(CONTENT_HASH, video.contentHash)
            .set(DSL.field("thumbnail_urls", JSONB::class.java), thumbnailJson)
            .set(STATUS, video.status.name)
            .returning()
            .fetchOne()!!

        return record.toVideo()
    }

    override fun update(video: Video): Video {
        val tagsArray = video.tags.toTypedArray()
        val thumbnailJson = JSONB.jsonb(objectMapper.writeValueAsString(video.thumbnailUrls))

        val record = dsl.update(VIDEOS)
            .set(TITLE, video.title)
            .set(DESCRIPTION, video.description)
            .set(DSL.field("tags", Array<String>::class.java), tagsArray)
            .set(CATEGORY, video.category)
            .set(FILE_URL, video.fileUrl)
            .set(FILE_SIZE_BYTES, video.fileSizeBytes)
            .set(DURATION_SECONDS, video.durationSeconds)
            .set(RESOLUTION, video.resolution)
            .set(ORIGINAL_FILENAME, video.originalFilename)
            .set(CONTENT_HASH, video.contentHash)
            .set(DSL.field("thumbnail_urls", JSONB::class.java), thumbnailJson)
            .set(STATUS, video.status.name)
            .where(ID.eq(video.id))
            .returning()
            .fetchOne()!!

        return record.toVideo()
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(VIDEOS)
            .where(ID.eq(id))
            .execute()
    }

    override fun findByContentHash(contentHash: String): Video? =
        dsl.select()
            .from(VIDEOS)
            .where(CONTENT_HASH.eq(contentHash))
            .fetchOne()
            ?.toVideo()

    @Suppress("UNCHECKED_CAST")
    private fun Record.toVideo(): Video {
        val tagsRaw = get("tags")
        val tags: List<String> = when (tagsRaw) {
            is Array<*> -> (tagsRaw as Array<String>).toList()
            else -> emptyList()
        }

        val thumbnailRaw = get("thumbnail_urls")
        val thumbnailUrls: List<String> = when (thumbnailRaw) {
            is JSONB -> parseThumbnailUrls(thumbnailRaw.data())
            is String -> parseThumbnailUrls(thumbnailRaw)
            else -> emptyList()
        }

        return Video(
            id = get(ID),
            userId = get(USER_ID),
            title = get(TITLE),
            description = get(DESCRIPTION),
            tags = tags,
            category = get(CATEGORY),
            fileUrl = get(FILE_URL),
            fileSizeBytes = get(FILE_SIZE_BYTES),
            durationSeconds = get(DURATION_SECONDS),
            resolution = get(RESOLUTION),
            originalFilename = get(ORIGINAL_FILENAME),
            contentHash = get(CONTENT_HASH),
            thumbnailUrls = thumbnailUrls,
            status = UploadStatus.valueOf(get(STATUS)),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }

    private fun parseThumbnailUrls(json: String): List<String> {
        if (json.isBlank() || json == "[]" || json == "null") return emptyList()
        return json.trim('[', ']')
            .split(",")
            .map { it.trim().trim('"') }
            .filter { it.isNotBlank() }
    }
}
