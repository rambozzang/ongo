package com.ongo.application.contentlibrary.dto

import com.ongo.domain.contentlibrary.LibraryItem
import com.ongo.domain.contentlibrary.LibraryFolder

data class LibraryItemResponse(
    val id: Long,
    val title: String,
    val type: String,
    val platform: String?,
    val thumbnailUrl: String?,
    val fileSize: Long,
    val tags: List<String>,
    val folderId: Long?,
    val folderName: String?,
    val uploadedAt: String,
    val updatedAt: String,
) {
    companion object {
        fun from(e: LibraryItem, folderName: String? = null) = LibraryItemResponse(
            id = e.id, title = e.title, type = e.type,
            platform = e.platform, thumbnailUrl = e.thumbnailUrl,
            fileSize = e.fileSize, tags = e.tags,
            folderId = e.folderId, folderName = folderName,
            uploadedAt = e.uploadedAt.toString(), updatedAt = e.updatedAt.toString(),
        )
    }
}

data class LibraryFolderResponse(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val itemCount: Int,
    val color: String,
    val createdAt: String,
) {
    companion object {
        fun from(e: LibraryFolder, itemCount: Int = 0) = LibraryFolderResponse(
            id = e.id, name = e.name, parentId = e.parentId,
            itemCount = itemCount, color = e.color,
            createdAt = e.createdAt.toString(),
        )
    }
}

data class ContentLibrarySummaryResponse(
    val totalItems: Int,
    val totalFolders: Int,
    val totalSize: Long,
    val videoCount: Int,
    val imageCount: Int,
)

data class CreateFolderRequest(
    val name: String,
    val parentId: Long? = null,
)
