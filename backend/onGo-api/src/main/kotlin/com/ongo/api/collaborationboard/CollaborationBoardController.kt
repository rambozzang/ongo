package com.ongo.api.collaborationboard

import com.ongo.application.collaborationboard.CollaborationBoardUseCase
import com.ongo.application.collaborationboard.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "협업 보드", description = "칸반 스타일 콘텐츠 제작 협업 보드")
@RestController
@RequestMapping("/api/v1/board")
class CollaborationBoardController(
    private val boardUseCase: CollaborationBoardUseCase
) {

    @Operation(summary = "보드 컬럼 조회")
    @GetMapping("/columns")
    fun getBoard(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam workspaceId: Long,
    ): ResponseEntity<ResData<List<BoardColumnResponse>>> {
        val result = boardUseCase.getBoard(workspaceId)
        return ResData.success(result)
    }

    @Operation(summary = "보드 요약 조회")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam workspaceId: Long,
    ): ResponseEntity<ResData<BoardSummaryResponse>> {
        val result = boardUseCase.getSummary(workspaceId)
        return ResData.success(result)
    }

    @Operation(summary = "태스크 생성")
    @PostMapping("/tasks")
    fun createTask(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam workspaceId: Long,
        @RequestBody request: CreateBoardTaskRequest,
    ): ResponseEntity<ResData<BoardTaskResponse>> {
        val result = boardUseCase.createTask(workspaceId, userId, request)
        return ResData.success(result, "태스크가 생성되었습니다")
    }

    @Operation(summary = "태스크 이동")
    @PostMapping("/tasks/move")
    fun moveTask(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam workspaceId: Long,
        @RequestBody request: MoveBoardTaskRequest,
    ): ResponseEntity<ResData<BoardTaskResponse>> {
        val result = boardUseCase.moveTask(workspaceId, userId, request)
        return ResData.success(result, "태스크가 이동되었습니다")
    }

    @Operation(summary = "태스크 삭제")
    @DeleteMapping("/tasks/{id}")
    fun deleteTask(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam workspaceId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        boardUseCase.deleteTask(workspaceId, id)
        return ResData.success(null, "태스크가 삭제되었습니다")
    }

    @Operation(summary = "활동 로그 조회")
    @GetMapping("/activities")
    fun getActivities(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam workspaceId: Long,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ResponseEntity<ResData<List<BoardActivityResponse>>> {
        val result = boardUseCase.getActivities(workspaceId, limit)
        return ResData.success(result)
    }
}
