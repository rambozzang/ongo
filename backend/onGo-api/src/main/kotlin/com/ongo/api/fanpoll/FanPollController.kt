package com.ongo.api.fanpoll

import com.ongo.application.fanpoll.FanPollUseCase
import com.ongo.application.fanpoll.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "팬 투표", description = "팬 대상 투표/설문 관리")
@RestController
@RequestMapping("/api/v1/fan-polls")
class FanPollController(
    private val useCase: FanPollUseCase
) {

    @Operation(summary = "투표 목록 조회")
    @GetMapping
    fun getPolls(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<FanPollResponse>>> {
        val result = useCase.getPolls(userId, status)
        return ResData.success(result)
    }

    @Operation(summary = "투표 상세 조회")
    @GetMapping("/{id}")
    fun getPoll(
        @PathVariable id: Long,
    ): ResponseEntity<ResData<FanPollResponse?>> {
        val result = useCase.getPoll(id)
        return ResData.success(result)
    }

    @Operation(summary = "투표 마감")
    @PatchMapping("/{id}/close")
    fun closePoll(
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Unit>> {
        useCase.closePoll(id)
        return ResData.success(Unit)
    }

    @Operation(summary = "투표 삭제")
    @DeleteMapping("/{id}")
    fun deletePoll(
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Unit>> {
        useCase.deletePoll(id)
        return ResData.success(Unit)
    }

    @Operation(summary = "투표 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<FanPollSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
