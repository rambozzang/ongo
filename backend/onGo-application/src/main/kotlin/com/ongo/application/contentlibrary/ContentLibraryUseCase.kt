package com.ongo.application.contentlibrary

import com.ongo.application.contentlibrary.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.contentlibrary.LibraryFolder
import com.ongo.domain.contentlibrary.LibraryFolderRepository
import com.ongo.domain.contentlibrary.LibraryItemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ContentLibraryUseCase(
    private val itemRepo: LibraryItemRepository,
    private val folderRepo: LibraryFolderRepository,
) {
    fun getItems(userId: Long, folderId: Long?, type: String?): List<LibraryItemResponse> {
        val items = when {
            folderId != null -> itemRepo.findByUserIdAndFolderId(userId, folderId)
            type != null -> itemRepo.findByUserIdAndType(userId, type)
            else -> itemRepo.findByUserId(userId)
        }
        return items.map { LibraryItemResponse.from(it) }
    }

    fun getFolders(userId: Long): List<LibraryFolderResponse> {
        val folders = folderRepo.findByUserId(userId)
        return folders.map { LibraryFolderResponse.from(it) }
    }

    @Transactional
    fun createFolder(userId: Long, req: CreateFolderRequest): LibraryFolderResponse {
        val folder = folderRepo.save(LibraryFolder(userId = userId, name = req.name, parentId = req.parentId))
        return LibraryFolderResponse.from(folder)
    }

    fun getItem(userId: Long, itemId: Long): LibraryItemResponse {
        val item = itemRepo.findById(itemId)
            ?: throw NotFoundException("라이브러리 아이템", itemId)
        if (item.userId != userId) throw ForbiddenException("해당 아이템에 대한 권한이 없습니다")
        val fid = item.folderId
        val folder = if (fid != null) folderRepo.findById(fid) else null
        return LibraryItemResponse.from(item, folder?.name)
    }

    @Transactional
    fun deleteItem(userId: Long, id: Long) {
        val item = itemRepo.findById(id)
            ?: throw NotFoundException("라이브러리 아이템", id)
        if (item.userId != userId) {
            throw ForbiddenException("해당 아이템에 대한 권한이 없습니다")
        }
        itemRepo.deleteById(id)
    }

    fun getSummary(userId: Long): ContentLibrarySummaryResponse {
        val items = itemRepo.findByUserId(userId)
        return ContentLibrarySummaryResponse(
            totalItems = items.size,
            totalFolders = folderRepo.findByUserId(userId).size,
            totalSize = items.sumOf { it.fileSize },
            videoCount = items.count { it.type == "VIDEO" },
            imageCount = items.count { it.type == "IMAGE" },
        )
    }
}
