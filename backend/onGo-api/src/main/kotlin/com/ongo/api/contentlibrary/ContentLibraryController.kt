package com.ongo.api.contentlibrary

import com.ongo.application.contentlibrary.ContentLibraryUseCase
import com.ongo.application.contentlibrary.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "콘텐츠 라이브러리", description = "업로드 콘텐츠 체계적 관리")
@RestController
@RequestMapping("/api/v1/content-library")
class ContentLibraryController(
    private val useCase: ContentLibraryUseCase
) {

    @Operation(summary = "라이브러리 아이템 목록 조회")
    @GetMapping
    fun getItems(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) folderId: Long?,
        @RequestParam(required = false) type: String?,
    ): ResponseEntity<ResData<List<LibraryItemResponse>>> {
        val result = useCase.getItems(userId, folderId, type)
        return ResData.success(result)
    }

    @Operation(summary = "폴더 목록 조회")
    @GetMapping("/folders")
    fun getFolders(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<LibraryFolderResponse>>> {
        val result = useCase.getFolders(userId)
        return ResData.success(result)
    }

    @Operation(summary = "폴더 생성")
    @PostMapping("/folders")
    fun createFolder(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateFolderRequest,
    ): ResponseEntity<ResData<LibraryFolderResponse>> {
        val result = useCase.createFolder(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "아이템 삭제")
    @DeleteMapping("/{id}")
    fun deleteItem(
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Unit>> {
        useCase.deleteItem(id)
        return ResData.success(Unit)
    }

    @Operation(summary = "라이브러리 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<ContentLibrarySummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
