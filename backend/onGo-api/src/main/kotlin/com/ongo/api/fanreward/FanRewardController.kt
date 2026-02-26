package com.ongo.api.fanreward

import com.ongo.application.fanreward.FanRewardUseCase
import com.ongo.application.fanreward.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "팬 리워드", description = "팬 활동 기반 리워드 시스템")
@RestController
@RequestMapping("/api/v1/fan-rewards")
class FanRewardController(
    private val fanRewardUseCase: FanRewardUseCase
) {

    @Operation(summary = "리워드 목록 조회")
    @GetMapping
    fun getRewards(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<FanRewardResponse>>> {
        val result = fanRewardUseCase.getRewards(userId)
        return ResData.success(result)
    }

    @Operation(summary = "리워드 생성")
    @PostMapping
    fun createReward(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateRewardRequest,
    ): ResponseEntity<ResData<FanRewardResponse>> {
        val result = fanRewardUseCase.createReward(userId, request)
        return ResData.success(result, "리워드가 생성되었습니다")
    }

    @Operation(summary = "팬 활동 조회")
    @GetMapping("/activities")
    fun getActivities(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<FanActivityResponse>>> {
        val result = fanRewardUseCase.getActivities(userId)
        return ResData.success(result)
    }

    @Operation(summary = "리워드 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<FanRewardSummaryResponse>> {
        val result = fanRewardUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
