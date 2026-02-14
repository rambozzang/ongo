package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.Platform
import com.ongo.common.enums.VariantStatus
import com.ongo.domain.video.VideoVariant
import com.ongo.domain.video.VideoVariantRepository
import com.ongo.infrastructure.persistence.jooq.Fields.BITRATE_KBPS
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ERROR_MESSAGE
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_SIZE_BYTES
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.HEIGHT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.WIDTH
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEO_VARIANTS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class VideoVariantJooqRepository(
    private val dsl: DSLContext,
) : VideoVariantRepository {

    override fun findById(id: Long): VideoVariant? =
        dsl.select()
            .from(VIDEO_VARIANTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toVideoVariant()

    override fun findByVideoId(videoId: Long): List<VideoVariant> =
        dsl.select()
            .from(VIDEO_VARIANTS)
            .where(VIDEO_ID.eq(videoId))
            .orderBy(CREATED_AT.asc())
            .fetch()
            .map { it.toVideoVariant() }

    override fun findByVideoIdAndPlatform(videoId: Long, platform: Platform): VideoVariant? =
        dsl.select()
            .from(VIDEO_VARIANTS)
            .where(VIDEO_ID.eq(videoId))
            .and(Fields.PLATFORM_TEXT.eq(platform.name))
            .fetchOne()
            ?.toVideoVariant()

    override fun findByVideoIds(videoIds: List<Long>): Map<Long, List<VideoVariant>> {
        if (videoIds.isEmpty()) return emptyMap()
        return dsl.select()
            .from(VIDEO_VARIANTS)
            .where(VIDEO_ID.`in`(videoIds))
            .orderBy(CREATED_AT.asc())
            .fetch()
            .map { it.toVideoVariant() }
            .groupBy { it.videoId }
    }

    override fun save(variant: VideoVariant): VideoVariant {
        val id = dsl.insertInto(VIDEO_VARIANTS)
            .set(VIDEO_ID, variant.videoId)
            .set(PLATFORM, variant.platform.name)
            .set(FILE_URL, variant.fileUrl)
            .set(FILE_SIZE_BYTES, variant.fileSizeBytes)
            .set(WIDTH, variant.width)
            .set(HEIGHT, variant.height)
            .set(BITRATE_KBPS, variant.bitrateKbps)
            .set(STATUS, variant.status.name)
            .set(ERROR_MESSAGE, variant.errorMessage)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(variant: VideoVariant): VideoVariant {
        dsl.update(VIDEO_VARIANTS)
            .set(FILE_URL, variant.fileUrl)
            .set(FILE_SIZE_BYTES, variant.fileSizeBytes)
            .set(WIDTH, variant.width)
            .set(HEIGHT, variant.height)
            .set(BITRATE_KBPS, variant.bitrateKbps)
            .set(STATUS, variant.status.name)
            .set(ERROR_MESSAGE, variant.errorMessage)
            .where(ID.eq(variant.id))
            .execute()

        return findById(variant.id!!)!!
    }

    override fun deleteByVideoId(videoId: Long) {
        dsl.deleteFrom(VIDEO_VARIANTS)
            .where(VIDEO_ID.eq(videoId))
            .execute()
    }

    private fun Record.toVideoVariant(): VideoVariant {
        val platformStr = get(PLATFORM) ?: "YOUTUBE"
        val statusStr = get(STATUS) ?: "PENDING"
        return VideoVariant(
            id = get(ID),
            videoId = get(VIDEO_ID),
            platform = try { Platform.valueOf(platformStr) } catch (_: Exception) { Platform.YOUTUBE },
            fileUrl = get(FILE_URL),
            fileSizeBytes = get(FILE_SIZE_BYTES),
            width = get(WIDTH),
            height = get(HEIGHT),
            bitrateKbps = get(BITRATE_KBPS),
            status = try { VariantStatus.valueOf(statusStr) } catch (_: Exception) { VariantStatus.PENDING },
            errorMessage = get(ERROR_MESSAGE),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
