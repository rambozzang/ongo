package com.ongo.api.moodboard

import com.ongo.application.moodboard.MoodBoardUseCase
import com.ongo.application.moodboard.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "크리에이터 무드보드", description = "콘텐츠 기획 비주얼 보드 관리")
@RestController
@RequestMapping("/api/v1/mood-boards")
class MoodBoardController(
    private val useCase: MoodBoardUseCase
) {

    @Operation(summary = "무드보드 목록 조회")
    @GetMapping
    fun getBoards(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<MoodBoardResponse>>> {
        val result = useCase.getBoards(userId)
        return ResData.success(result)
    }

    @Operation(summary = "무드보드 상세 조회")
    @GetMapping("/{id}")
    fun getBoard(
        @PathVariable id: Long,
    ): ResponseEntity<ResData<MoodBoardResponse?>> {
        val result = useCase.getBoard(id)
        return ResData.success(result)
    }

    @Operation(summary = "무드보드 생성")
    @PostMapping
    fun createBoard(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateMoodBoardRequest,
    ): ResponseEntity<ResData<MoodBoardResponse>> {
        val result = useCase.createBoard(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "무드보드 아이템 목록 조회")
    @GetMapping("/{boardId}/items")
    fun getItems(
        @PathVariable boardId: Long,
    ): ResponseEntity<ResData<List<MoodBoardItemResponse>>> {
        val result = useCase.getItems(boardId)
        return ResData.success(result)
    }

    @Operation(summary = "무드보드 삭제")
    @DeleteMapping("/{id}")
    fun deleteBoard(
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Unit>> {
        useCase.deleteBoard(id)
        return ResData.success(Unit)
    }

    @Operation(summary = "무드보드 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<MoodBoardSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
