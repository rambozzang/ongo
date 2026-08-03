package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.shorts.ClipPublication
import com.ongo.domain.ugc.shorts.ClipPublicationRepository
import com.ongo.domain.ugc.shorts.ClipPublicationStatus
import com.ongo.infrastructure.persistence.jooq.Fields.CLIP_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ERROR_MESSAGE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.PUBLISHED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.SCHEDULED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_UPLOAD_ID
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SHORTS_CLIP_PUBLICATIONS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class ShortsClipPublicationJooqRepository(
    private val dsl: DSLContext,
) : ClipPublicationRepository {

    override fun findByClipIdAndPlatform(clipId: Long, platform: String): ClipPublication? =
        dsl.select()
            .from(UGC_SHORTS_CLIP_PUBLICATIONS)
            .where(CLIP_ID.eq(clipId))
            .and(PLATFORM.eq(platform))
            .fetchOne()
            ?.toPublication()

    override fun save(publication: ClipPublication): ClipPublication {
        val id = dsl.insertInto(UGC_SHORTS_CLIP_PUBLICATIONS)
            .set(CLIP_ID, publication.clipId)
            .set(PLATFORM, publication.platform)
            .set(VIDEO_UPLOAD_ID, publication.videoUploadId)
            .set(STATUS, publication.status.name)
            .set(SCHEDULED_AT, publication.scheduledAt?.toLocalDateTime())
            .set(PUBLISHED_AT, publication.publishedAt?.toLocalDateTime())
            .set(ERROR_MESSAGE, publication.errorMessage)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return findById(id)!!
    }

    override fun update(publication: ClipPublication): ClipPublication {
        dsl.update(UGC_SHORTS_CLIP_PUBLICATIONS)
            .set(VIDEO_UPLOAD_ID, publication.videoUploadId)
            .set(STATUS, publication.status.name)
            .set(SCHEDULED_AT, publication.scheduledAt?.toLocalDateTime())
            .set(PUBLISHED_AT, publication.publishedAt?.toLocalDateTime())
            .set(ERROR_MESSAGE, publication.errorMessage)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(publication.id))
            .execute()
        return findById(publication.id)!!
    }

    private fun findById(id: Long): ClipPublication? =
        dsl.select()
            .from(UGC_SHORTS_CLIP_PUBLICATIONS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toPublication()

    private fun Record.toPublication() = ClipPublication(
        id = get(ID),
        clipId = get(CLIP_ID),
        platform = get(PLATFORM),
        videoUploadId = get(VIDEO_UPLOAD_ID),
        status = ClipPublicationStatus.valueOf(get(STATUS)),
        scheduledAt = localDateTime(SCHEDULED_AT)?.toInstant(),
        publishedAt = localDateTime(PUBLISHED_AT)?.toInstant(),
        errorMessage = get(ERROR_MESSAGE),
        createdAt = localDateTime(CREATED_AT)!!.toInstant(),
        updatedAt = localDateTime(UPDATED_AT)!!.toInstant(),
    )

    private fun Instant.toLocalDateTime() = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
    private fun LocalDateTime.toInstant() = atZone(ZoneOffset.UTC).toInstant()
}
