package com.ongo.api.fanfunding

import com.ongo.application.fanfunding.FanFundingUseCase
import com.ongo.application.fanfunding.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "팬 펀딩", description = "팬 펀딩 수익 추적 및 목표 관리")
@RestController
@RequestMapping("/api/v1/fan-funding")
class FanFundingController(
    private val fanFundingUseCase: FanFundingUseCase
) {

    @Operation(summary = "펀딩 요약 조회")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) period: String?,
    ): ResponseEntity<ResData<FundingSummaryResponse>> {
        val result = fanFundingUseCase.getSummary(userId, period)
        return ResData.success(result)
    }

    @Operation(summary = "거래 내역 조회")
    @GetMapping("/transactions")
    fun getTransactions(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) source: String?,
        @RequestParam(required = false) platform: String?,
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
    ): ResponseEntity<ResData<List<FundingTransactionResponse>>> {
        val result = fanFundingUseCase.getTransactions(userId, source, platform, dateFrom, dateTo)
        return ResData.success(result)
    }

    @Operation(summary = "펀딩 목표 조회")
    @GetMapping("/goals")
    fun getGoals(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<FundingGoalResponse>>> {
        val result = fanFundingUseCase.getGoals(userId)
        return ResData.success(result)
    }

    @Operation(summary = "펀딩 목표 생성")
    @PostMapping("/goals")
    fun createGoal(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateGoalRequest,
    ): ResponseEntity<ResData<FundingGoalResponse>> {
        val result = fanFundingUseCase.createGoal(userId, request)
        return ResData.success(result, "펀딩 목표가 생성되었습니다")
    }

    @Operation(summary = "펀딩 목표 수정")
    @PutMapping("/goals/{id}")
    fun updateGoal(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateGoalRequest,
    ): ResponseEntity<ResData<FundingGoalResponse>> {
        val result = fanFundingUseCase.updateGoal(userId, id, request)
        return ResData.success(result, "펀딩 목표가 수정되었습니다")
    }

    @Operation(summary = "펀딩 목표 삭제")
    @DeleteMapping("/goals/{id}")
    fun deleteGoal(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        fanFundingUseCase.deleteGoal(userId, id)
        return ResData.success(null, "펀딩 목표가 삭제되었습니다")
    }
}
