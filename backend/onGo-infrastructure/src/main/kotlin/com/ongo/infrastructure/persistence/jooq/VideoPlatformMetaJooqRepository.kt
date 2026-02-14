package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.Visibility
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.domain.video.VideoPlatformMetaRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CUSTOM_THUMBNAIL_URL
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.TAGS
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_UPLOAD_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VISIBILITY
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEO_PLATFORM_META
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class VideoPlatformMetaJooqRepository(
    private val dsl: DSLContext,
) : VideoPlatformMetaRepository {

    private fun findById(id: Long): VideoPlatformMeta? =
        dsl.select()
            .from(VIDEO_PLATFORM_META)
            .where(ID.eq(id))
            .fetchOne()
            ?.toVideoPlatformMeta()

    override fun findByVideoUploadId(videoUploadId: Long): VideoPlatformMeta? =
        dsl.select()
            .from(VIDEO_PLATFORM_META)
            .where(VIDEO_UPLOAD_ID.eq(videoUploadId))
            .fetchOne()
            ?.toVideoPlatformMeta()

    override fun findByVideoUploadIds(videoUploadIds: List<Long>): Map<Long, VideoPlatformMeta> {
        if (videoUploadIds.isEmpty()) return emptyMap()
        return dsl.select()
            .from(VIDEO_PLATFORM_META)
            .where(VIDEO_UPLOAD_ID.`in`(videoUploadIds))
            .fetch()
            .map { it.toVideoPlatformMeta() }
            .associateBy { it.videoUploadId }
    }

    override fun save(meta: VideoPlatformMeta): VideoPlatformMeta {
        val tagsArray = meta.tags.toTypedArray()

        val id = dsl.insertInto(VIDEO_PLATFORM_META)
            .set(VIDEO_UPLOAD_ID, meta.videoUploadId)
            .set(TITLE, meta.title)
            .set(DESCRIPTION, meta.description)
            .set(DSL.field("tags", Array<String>::class.java), tagsArray)
            .set(VISIBILITY, meta.visibility.name)
            .set(CUSTOM_THUMBNAIL_URL, meta.customThumbnailUrl)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(meta: VideoPlatformMeta): VideoPlatformMeta {
        val tagsArray = meta.tags.toTypedArray()

        dsl.update(VIDEO_PLATFORM_META)
            .set(TITLE, meta.title)
            .set(DESCRIPTION, meta.description)
            .set(DSL.field("tags", Array<String>::class.java), tagsArray)
            .set(VISIBILITY, meta.visibility.name)
            .set(CUSTOM_THUMBNAIL_URL, meta.customThumbnailUrl)
            .where(ID.eq(meta.id))
            .execute()

        return findById(meta.id!!)!!
    }

    @Suppress("UNCHECKED_CAST")
    private fun Record.toVideoPlatformMeta(): VideoPlatformMeta {
        val tagsRaw = get("tags")
        val tags: List<String> = when (tagsRaw) {
            is Array<*> -> (tagsRaw as Array<String>).toList()
            is java.sql.Array -> (tagsRaw.array as Array<String>).toList()
            else -> emptyList()
        }

        val visibilityStr = get(VISIBILITY) ?: "PUBLIC"
        return VideoPlatformMeta(
            id = get(ID),
            videoUploadId = get(VIDEO_UPLOAD_ID),
            title = get(TITLE),
            description = get(DESCRIPTION),
            tags = tags,
            visibility = try { Visibility.valueOf(visibilityStr) } catch (_: Exception) { Visibility.PUBLIC },
            customThumbnailUrl = get(CUSTOM_THUMBNAIL_URL),
        )
    }
}
