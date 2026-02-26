package com.ongo.api.revenuesplit

import com.ongo.application.revenuesplit.RevenueSplitUseCase
import com.ongo.application.revenuesplit.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "수익 분배", description = "팀 수익 분배 관리")
@RestController
@RequestMapping("/api/v1/revenue-splits")
class RevenueSplitController(
    private val useCase: RevenueSplitUseCase
) {

    @Operation(summary = "수익 분배 목록 조회")
    @GetMapping
    fun getSplits(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<RevenueSplitResponse>>> {
        val result = useCase.getSplits(userId, status)
        return ResData.success(result)
    }

    @Operation(summary = "수익 분배 상세 조회")
    @GetMapping("/{id}")
    fun getSplit(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<RevenueSplitResponse>> {
        val result = useCase.getSplit(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "수익 분배 생성")
    @PostMapping
    fun createSplit(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateRevenueSplitRequest,
    ): ResponseEntity<ResData<RevenueSplitResponse>> {
        val result = useCase.createSplit(userId, request)
        return ResData.success(result, "수익 분배가 생성되었습니다")
    }

    @Operation(summary = "수익 분배 삭제")
    @DeleteMapping("/{id}")
    fun deleteSplit(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        useCase.deleteSplit(userId, id)
        return ResData.success(null, "수익 분배가 삭제되었습니다")
    }

    @Operation(summary = "수익 분배 승인")
    @PostMapping("/{id}/approve")
    fun approveSplit(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<RevenueSplitResponse>> {
        val result = useCase.approveSplit(userId, id)
        return ResData.success(result, "수익 분배가 승인되었습니다")
    }

    @Operation(summary = "수익 분배 실행")
    @PostMapping("/{id}/distribute")
    fun distributeSplit(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<RevenueSplitResponse>> {
        val result = useCase.distributeSplit(userId, id)
        return ResData.success(result, "수익이 분배되었습니다")
    }

    @Operation(summary = "수익 분배 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<RevenueSplitSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
