package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.video.ContentImage
import com.ongo.domain.video.ContentImageRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CONTENT_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DISPLAY_ORDER
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_SIZE_BYTES
import com.ongo.infrastructure.persistence.jooq.Fields.HEIGHT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IMAGE_URL_CI
import com.ongo.infrastructure.persistence.jooq.Fields.ORIGINAL_FILENAME
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.WIDTH
import com.ongo.infrastructure.persistence.jooq.Tables.CONTENT_IMAGES
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class ContentImageJooqRepository(
    private val dsl: DSLContext,
) : ContentImageRepository {

    override fun save(image: ContentImage): ContentImage {
        val id = dsl.insertInto(CONTENT_IMAGES)
            .set(VIDEO_ID, image.videoId)
            .set(IMAGE_URL_CI, image.imageUrl)
            .set(DISPLAY_ORDER, image.displayOrder)
            .set(WIDTH, image.width)
            .set(HEIGHT, image.height)
            .set(FILE_SIZE_BYTES, image.fileSizeBytes)
            .set(ORIGINAL_FILENAME, image.originalFilename)
            .set(CONTENT_TYPE, image.contentType)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun saveAll(images: List<ContentImage>): List<ContentImage> {
        if (images.isEmpty()) return emptyList()

        val ids = images.map { image ->
            dsl.insertInto(CONTENT_IMAGES)
                .set(VIDEO_ID, image.videoId)
                .set(IMAGE_URL_CI, image.imageUrl)
                .set(DISPLAY_ORDER, image.displayOrder)
                .set(WIDTH, image.width)
                .set(HEIGHT, image.height)
                .set(FILE_SIZE_BYTES, image.fileSizeBytes)
                .set(ORIGINAL_FILENAME, image.originalFilename)
                .set(CONTENT_TYPE, image.contentType)
                .returningResult(ID)
                .fetchOne()!!
                .get(ID)
        }

        return dsl.select()
            .from(CONTENT_IMAGES)
            .where(ID.`in`(ids))
            .orderBy(DISPLAY_ORDER.asc())
            .fetch()
            .map { it.toContentImage() }
    }

    override fun findByVideoId(videoId: Long): List<ContentImage> =
        dsl.select()
            .from(CONTENT_IMAGES)
            .where(VIDEO_ID.eq(videoId))
            .orderBy(DISPLAY_ORDER.asc())
            .fetch()
            .map { it.toContentImage() }

    override fun deleteByVideoId(videoId: Long) {
        dsl.deleteFrom(CONTENT_IMAGES)
            .where(VIDEO_ID.eq(videoId))
            .execute()
    }

    override fun updateOrder(videoId: Long, imageIds: List<Long>) {
        imageIds.forEachIndexed { index, imageId ->
            dsl.update(CONTENT_IMAGES)
                .set(DISPLAY_ORDER, index)
                .where(ID.eq(imageId))
                .and(VIDEO_ID.eq(videoId))
                .execute()
        }
    }

    private fun findById(id: Long): ContentImage? =
        dsl.select()
            .from(CONTENT_IMAGES)
            .where(ID.eq(id))
            .fetchOne()
            ?.toContentImage()

    private fun Record.toContentImage(): ContentImage =
        ContentImage(
            id = get(ID),
            videoId = get(VIDEO_ID),
            imageUrl = get(IMAGE_URL_CI),
            displayOrder = get(DISPLAY_ORDER) ?: 0,
            width = get(WIDTH),
            height = get(HEIGHT),
            fileSizeBytes = get(FILE_SIZE_BYTES),
            originalFilename = get(ORIGINAL_FILENAME),
            contentType = get(CONTENT_TYPE),
            createdAt = localDateTime(CREATED_AT),
        )
}
