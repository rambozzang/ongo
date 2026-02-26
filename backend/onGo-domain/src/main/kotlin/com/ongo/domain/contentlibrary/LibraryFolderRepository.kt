package com.ongo.domain.contentlibrary

interface LibraryFolderRepository {
    fun findByUserId(userId: Long): List<LibraryFolder>
    fun findById(id: Long): LibraryFolder?
    fun save(folder: LibraryFolder): LibraryFolder
    fun deleteById(id: Long)
}
