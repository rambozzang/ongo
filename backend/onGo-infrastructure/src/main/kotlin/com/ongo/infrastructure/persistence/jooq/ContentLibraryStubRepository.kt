package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentlibrary.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.LIBRARY_FOLDERS
import com.ongo.infrastructure.persistence.jooq.Tables.LIBRARY_ITEMS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.ZoneId

@Repository
class LibraryFolderJooqRepository(
    private val dsl: DSLContext,
) : LibraryFolderRepository {

    companion object {
        private val PARENT_ID = DSL.field("parent_id", Long::class.java)
        private val COLOR = DSL.field("color", String::class.java)
    }

    override fun findByUserId(userId: Long): List<LibraryFolder> =
        dsl.select()
            .from(LIBRARY_FOLDERS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun findById(id: Long): LibraryFolder? =
        dsl.select()
            .from(LIBRARY_FOLDERS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toEntity()

    override fun save(folder: LibraryFolder): LibraryFolder {
        val id = dsl.insertInto(LIBRARY_FOLDERS)
            .set(USER_ID, folder.userId)
            .set(NAME, folder.name)
            .set(PARENT_ID, folder.parentId)
            .set(COLOR, folder.color)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(LIBRARY_FOLDERS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toEntity(): LibraryFolder {
        val createdAtLdt = localDateTime(CREATED_AT)
        return LibraryFolder(
            id = get(ID),
            userId = get(USER_ID),
            name = get(NAME),
            parentId = get(PARENT_ID),
            color = get(COLOR) ?: "#3B82F6",
            createdAt = createdAtLdt?.atZone(ZoneId.systemDefault())?.toInstant() ?: Instant.now(),
        )
    }
}

@Repository
class LibraryItemJooqRepository(
    private val dsl: DSLContext,
) : LibraryItemRepository {

    companion object {
        private val THUMBNAIL_URL = DSL.field("thumbnail_url", String::class.java)
        private val FILE_SIZE = DSL.field("file_size", Long::class.java)
        private val TAGS = DSL.field("tags", String::class.java)
        private val FOLDER_ID = DSL.field("folder_id", Long::class.java)
        private val UPLOADED_AT = DSL.field("uploaded_at", java.time.LocalDateTime::class.java)
    }

    override fun findByUserId(userId: Long): List<LibraryItem> =
        dsl.select()
            .from(LIBRARY_ITEMS)
            .where(USER_ID.eq(userId))
            .orderBy(UPLOADED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun findById(id: Long): LibraryItem? =
        dsl.select()
            .from(LIBRARY_ITEMS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toEntity()

    override fun findByUserIdAndFolderId(userId: Long, folderId: Long?): List<LibraryItem> =
        dsl.select()
            .from(LIBRARY_ITEMS)
            .where(USER_ID.eq(userId))
            .and(if (folderId != null) FOLDER_ID.eq(folderId) else FOLDER_ID.isNull)
            .orderBy(UPLOADED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun findByUserIdAndType(userId: Long, type: String): List<LibraryItem> =
        dsl.select()
            .from(LIBRARY_ITEMS)
            .where(USER_ID.eq(userId))
            .and(TYPE.eq(type))
            .orderBy(UPLOADED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun save(item: LibraryItem): LibraryItem {
        val tagsJson = item.tags.joinToString(",")
        val id = dsl.insertInto(LIBRARY_ITEMS)
            .set(USER_ID, item.userId)
            .set(TITLE, item.title)
            .set(TYPE, item.type)
            .set(PLATFORM, item.platform)
            .set(THUMBNAIL_URL, item.thumbnailUrl)
            .set(FILE_SIZE, item.fileSize)
            .set(TAGS, tagsJson)
            .set(FOLDER_ID, item.folderId)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(LIBRARY_ITEMS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toEntity(): LibraryItem {
        val uploadedAtLdt = localDateTime(UPLOADED_AT)
        val updatedAtLdt = localDateTime(UPDATED_AT)
        val tagsStr = get(TAGS) ?: ""
        val tagsList = if (tagsStr.isBlank()) emptyList() else tagsStr.split(",").map { it.trim() }
        return LibraryItem(
            id = get(ID),
            userId = get(USER_ID),
            title = get(TITLE) ?: "",
            type = get(TYPE) ?: "",
            platform = get(PLATFORM),
            thumbnailUrl = get(THUMBNAIL_URL),
            fileSize = get(FILE_SIZE) ?: 0,
            tags = tagsList,
            folderId = get(FOLDER_ID),
            uploadedAt = uploadedAtLdt?.atZone(ZoneId.systemDefault())?.toInstant() ?: Instant.now(),
            updatedAt = updatedAtLdt?.atZone(ZoneId.systemDefault())?.toInstant() ?: Instant.now(),
        )
    }
}
