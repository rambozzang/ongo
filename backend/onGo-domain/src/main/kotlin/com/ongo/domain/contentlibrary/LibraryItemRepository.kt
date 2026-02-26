package com.ongo.domain.contentlibrary

interface LibraryItemRepository {
    fun findByUserId(userId: Long): List<LibraryItem>
    fun findById(id: Long): LibraryItem?
    fun findByUserIdAndFolderId(userId: Long, folderId: Long?): List<LibraryItem>
    fun findByUserIdAndType(userId: Long, type: String): List<LibraryItem>
    fun save(item: LibraryItem): LibraryItem
    fun deleteById(id: Long)
}
