package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.asset.Asset
import com.ongo.domain.asset.AssetRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DURATION_SECONDS
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_SIZE_BYTES
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.FILENAME
import com.ongo.infrastructure.persistence.jooq.Fields.FOLDER
import com.ongo.infrastructure.persistence.jooq.Fields.HEIGHT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.MIME_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.ORIGINAL_FILENAME
import com.ongo.infrastructure.persistence.jooq.Fields.TAGS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.WIDTH
import com.ongo.infrastructure.persistence.jooq.Tables.ASSETS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class AssetJooqRepository(
    private val dsl: DSLContext,
) : AssetRepository {

    override fun findById(id: Long): Asset? =
        dsl.select()
            .from(ASSETS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toAsset()

    override fun findByUserId(userId: Long, page: Int, size: Int): List<Asset> =
        dsl.select()
            .from(ASSETS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toAsset() }

    override fun findByUserIdAndFileType(userId: Long, fileType: String, page: Int, size: Int): List<Asset> =
        dsl.select()
            .from(ASSETS)
            .where(USER_ID.eq(userId))
            .and(FILE_TYPE.eq(fileType))
            .orderBy(CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toAsset() }

    override fun findByUserIdAndFolder(userId: Long, folder: String, page: Int, size: Int): List<Asset> =
        dsl.select()
            .from(ASSETS)
            .where(USER_ID.eq(userId))
            .and(FOLDER.eq(folder))
            .orderBy(CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toAsset() }

    override fun countByUserId(userId: Long): Int =
        dsl.selectCount()
            .from(ASSETS)
            .where(USER_ID.eq(userId))
            .fetchOne(0, Int::class.java) ?: 0

    override fun save(asset: Asset): Asset {
        val record = dsl.insertInto(ASSETS)
            .set(USER_ID, asset.userId)
            .set(FILENAME, asset.filename)
            .set(ORIGINAL_FILENAME, asset.originalFilename)
            .set(FILE_URL, asset.fileUrl)
            .set(FILE_TYPE, asset.fileType)
            .set(FILE_SIZE_BYTES, asset.fileSizeBytes)
            .set(MIME_TYPE, asset.mimeType)
            .set(TAGS, asset.tags.toTypedArray())
            .set(FOLDER, asset.folder)
            .set(WIDTH, asset.width)
            .set(HEIGHT, asset.height)
            .set(DURATION_SECONDS, asset.durationSeconds)
            .returning()
            .fetchOne()!!

        return record.toAsset()
    }

    override fun update(asset: Asset): Asset {
        val record = dsl.update(ASSETS)
            .set(TAGS, asset.tags.toTypedArray())
            .set(FOLDER, asset.folder)
            .where(ID.eq(asset.id))
            .returning()
            .fetchOne()!!

        return record.toAsset()
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(ASSETS)
            .where(ID.eq(id))
            .execute()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Record.toAsset(): Asset = Asset(
        id = get(ID),
        userId = get(USER_ID),
        filename = get(FILENAME),
        originalFilename = get(ORIGINAL_FILENAME),
        fileUrl = get(FILE_URL),
        fileType = get(FILE_TYPE),
        fileSizeBytes = get(FILE_SIZE_BYTES),
        mimeType = get(MIME_TYPE),
        tags = (get(TAGS) as? Array<String>)?.toList() ?: emptyList(),
        folder = get(FOLDER) ?: "default",
        width = get(WIDTH),
        height = get(HEIGHT),
        durationSeconds = get(DURATION_SECONDS),
        createdAt = localDateTime(CREATED_AT),
    )
}
