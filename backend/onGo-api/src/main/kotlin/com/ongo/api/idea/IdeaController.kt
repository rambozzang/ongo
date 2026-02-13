package com.ongo.api.idea

import com.ongo.application.idea.IdeaUseCase
import com.ongo.application.idea.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "아이디어 관리", description = "콘텐츠 아이디어 CRUD 및 상태 관리")
@RestController
@RequestMapping("/api/v1/ideas")
class IdeaController(
    private val ideaUseCase: IdeaUseCase
) {

    @Operation(summary = "아이디어 목록 조회")
    @GetMapping
    fun listIdeas(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) priority: String?,
    ): ResponseEntity<ResData<List<IdeaResponse>>> {
        val result = ideaUseCase.listIdeas(userId, status, category, priority)
        return ResData.success(result)
    }

    @Operation(summary = "아이디어 생성")
    @PostMapping
    fun createIdea(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateIdeaRequest,
    ): ResponseEntity<ResData<IdeaResponse>> {
        val result = ideaUseCase.createIdea(userId, request)
        return ResData.success(result, "아이디어가 생성되었습니다")
    }

    @Operation(summary = "아이디어 수정")
    @PutMapping("/{id}")
    fun updateIdea(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateIdeaRequest,
    ): ResponseEntity<ResData<IdeaResponse>> {
        val result = ideaUseCase.updateIdea(userId, id, request)
        return ResData.success(result)
    }

    @Operation(summary = "아이디어 삭제")
    @DeleteMapping("/{id}")
    fun deleteIdea(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        ideaUseCase.deleteIdea(userId, id)
        return ResData.success(null, "아이디어가 삭제되었습니다")
    }

    @Operation(summary = "아이디어 상태 변경")
    @PutMapping("/{id}/status")
    fun changeStatus(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: ChangeIdeaStatusRequest,
    ): ResponseEntity<ResData<IdeaResponse>> {
        val result = ideaUseCase.changeStatus(userId, id, request)
        return ResData.success(result)
    }
}
