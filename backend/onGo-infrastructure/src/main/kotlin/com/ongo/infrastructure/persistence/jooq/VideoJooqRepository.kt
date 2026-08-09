package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.MediaType
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.contentsource.VideoSource
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CATEGORY
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_SIZE_BYTES
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.ORIGINAL_FILENAME
import com.ongo.infrastructure.persistence.jooq.Fields.SOURCE
import com.ongo.infrastructure.persistence.jooq.Fields.SOURCE_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.TAGS
import com.ongo.infrastructure.persistence.jooq.Fields.MEDIA_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.MEDIA_TYPE_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.THUMBNAIL_URLS
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEOS
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
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

    override fun findByIds(ids: List<Long>): List<Video> {
        if (ids.isEmpty()) return emptyList()
        return dsl.select()
            .from(VIDEOS)
            .where(ID.`in`(ids))
            .fetch()
            .map { it.toVideo() }
    }

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
        val sourceRefJson = video.sourceReference?.let { JSONB.jsonb(objectMapper.writeValueAsString(it)) }

        val id = dsl.insertInto(VIDEOS)
            .set(USER_ID, video.userId)
            .set(TITLE, video.title)
            .set(DESCRIPTION, video.description)
            .set(DSL.field("tags", Array<String>::class.java), tagsArray)
            .set(CATEGORY, video.category)
            .set(FILE_URL, video.fileUrl)
            .set(FILE_SIZE_BYTES, video.fileSizeBytes)
            .set(ORIGINAL_FILENAME, video.originalFilename)
            .set(DSL.field("thumbnail_urls", JSONB::class.java), thumbnailJson)
            .set(MEDIA_TYPE, video.mediaType.name)
            .set(STATUS, video.status.name)
            .set(SOURCE, video.source.name)
            .set(DSL.field("source_reference", JSONB::class.java), sourceRefJson)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(video: Video): Video {
        val tagsArray = video.tags.toTypedArray()
        val thumbnailJson = JSONB.jsonb(objectMapper.writeValueAsString(video.thumbnailUrls))
        val sourceRefJson = video.sourceReference?.let { JSONB.jsonb(objectMapper.writeValueAsString(it)) }

        dsl.update(VIDEOS)
            .set(TITLE, video.title)
            .set(DESCRIPTION, video.description)
            .set(DSL.field("tags", Array<String>::class.java), tagsArray)
            .set(CATEGORY, video.category)
            .set(FILE_URL, video.fileUrl)
            .set(FILE_SIZE_BYTES, video.fileSizeBytes)
            .set(ORIGINAL_FILENAME, video.originalFilename)
            .set(DSL.field("thumbnail_urls", JSONB::class.java), thumbnailJson)
            .set(MEDIA_TYPE, video.mediaType.name)
            .set(STATUS, video.status.name)
            .set(SOURCE, video.source.name)
            .set(DSL.field("source_reference", JSONB::class.java), sourceRefJson)
            .where(ID.eq(video.id))
            .execute()

        return findById(video.id!!)!!
    }

    /**
     * Serialize concurrent publish requests at the database boundary.
     * A read-then-update check in the use case is not enough: two requests can
     * both observe DRAFT before either one writes its upload rows.
     */
    override fun claimForPublish(userId: Long, videoId: Long): Boolean =
        dsl.update(VIDEOS)
            .set(STATUS, UploadStatus.UPLOADING.name)
            .where(ID.eq(videoId))
            .and(USER_ID.eq(userId))
            .and(STATUS_TEXT.eq(UploadStatus.DRAFT.name))
            .execute() == 1

    override fun delete(id: Long) {
        dsl.deleteFrom(VIDEOS)
            .where(ID.eq(id))
            .execute()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Record.toVideo(): Video {
        val tagsRaw = get("tags")
        val tags: List<String> = when (tagsRaw) {
            is Array<*> -> (tagsRaw as Array<String>).toList()
            is java.sql.Array -> (tagsRaw.array as Array<String>).toList()
            else -> emptyList()
        }

        val thumbnailRaw = get("thumbnail_urls")
        val thumbnailUrls: List<String> = when (thumbnailRaw) {
            is JSONB -> parseThumbnailUrls(thumbnailRaw.data())
            is String -> parseThumbnailUrls(thumbnailRaw)
            else -> emptyList()
        }

        val statusStr = get(STATUS) ?: "DRAFT"
        val mediaTypeStr = get(MEDIA_TYPE) ?: "VIDEO"

        val sourceStr = runCatching { get(SOURCE_TEXT) }.getOrNull() ?: "UPLOAD_PC"
        val sourceReferenceJson: JsonNode? = runCatching {
            val raw = get("source_reference")
            when (raw) {
                null -> null
                is JSONB -> {
                    val data = raw.data()
                    if (data.isNullOrBlank() || data == "null") null else objectMapper.readTree(data)
                }
                is String -> {
                    if (raw.isBlank() || raw == "null") null else objectMapper.readTree(raw)
                }
                else -> {
                    val s = raw.toString()
                    if (s.isBlank() || s == "null") null else objectMapper.readTree(s)
                }
            }
        }.getOrNull()

        return Video(
            id = get(ID),
            userId = get(USER_ID),
            title = get(TITLE),
            description = get(DESCRIPTION),
            tags = tags,
            category = get(CATEGORY),
            fileUrl = get(FILE_URL),
            fileSizeBytes = get(FILE_SIZE_BYTES),
            originalFilename = get(ORIGINAL_FILENAME),
            thumbnailUrls = thumbnailUrls,
            mediaType = try { MediaType.valueOf(mediaTypeStr) } catch (_: Exception) { MediaType.VIDEO },
            status = try { UploadStatus.valueOf(statusStr) } catch (_: Exception) { UploadStatus.DRAFT },
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
            source = try { VideoSource.valueOf(sourceStr) } catch (_: Exception) { VideoSource.UPLOAD_PC },
            sourceReference = sourceReferenceJson,
        )
    }

    private fun parseThumbnailUrls(json: String): List<String> {
        if (json.isBlank() || json == "null") return emptyList()
        return try {
            objectMapper.readValue(json, object : TypeReference<List<String>>() {})
        } catch (_: Exception) {
            emptyList()
        }
    }
}
