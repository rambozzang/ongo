package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentlibrary.*
import org.springframework.stereotype.Repository

@Repository
class LibraryFolderStubRepository : LibraryFolderRepository {
    override fun findByUserId(userId: Long): List<LibraryFolder> = emptyList()
    override fun findById(id: Long): LibraryFolder? = null
    override fun save(folder: LibraryFolder): LibraryFolder = folder.copy(id = 1)
    override fun deleteById(id: Long) {}
}

@Repository
class LibraryItemStubRepository : LibraryItemRepository {
    override fun findByUserId(userId: Long): List<LibraryItem> = emptyList()
    override fun findById(id: Long): LibraryItem? = null
    override fun findByUserIdAndFolderId(userId: Long, folderId: Long?): List<LibraryItem> = emptyList()
    override fun findByUserIdAndType(userId: Long, type: String): List<LibraryItem> = emptyList()
    override fun save(item: LibraryItem): LibraryItem = item.copy(id = 1)
    override fun deleteById(id: Long) {}
}
