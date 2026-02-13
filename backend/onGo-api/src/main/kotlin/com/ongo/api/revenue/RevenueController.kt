package com.ongo.api.revenue

import com.ongo.application.revenue.RevenueUseCase
import com.ongo.application.revenue.dto.PlatformRevenueResponse
import com.ongo.application.revenue.dto.RevenueSummaryResponse
import com.ongo.application.revenue.dto.RevenueTrendResponse
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "수익 분석", description = "수익 요약, 트렌드, 플랫폼별 수익 분석")
@RestController
@RequestMapping("/api/v1/analytics/revenue")
class RevenueController(
    private val revenueUseCase: RevenueUseCase,
) {

    @Operation(summary = "수익 요약 조회")
    @GetMapping
    fun getRevenueSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<ResData<RevenueSummaryResponse>> {
        return ResData.success(revenueUseCase.getRevenueSummary(userId, days))
    }

    @Operation(summary = "수익 트렌드 조회")
    @GetMapping("/trends")
    fun getRevenueTrends(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<ResData<RevenueTrendResponse>> {
        return ResData.success(revenueUseCase.getRevenueTrends(userId, days))
    }

    @Operation(summary = "플랫폼별 수익 비교")
    @GetMapping("/platform")
    fun getPlatformRevenue(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<ResData<PlatformRevenueResponse>> {
        return ResData.success(revenueUseCase.getPlatformRevenue(userId, days))
    }
}
