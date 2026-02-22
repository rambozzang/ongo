package com.ongo.api.revenue

import com.ongo.application.revenue.RevenueUseCase
import com.ongo.application.revenue.dto.*
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

    @Operation(summary = "플랫폼별 CPM/RPM 조회", description = "플랫폼별 1000회 노출/조회당 수익(CPM/RPM)을 조회합니다.")
    @GetMapping("/cpm-rpm")
    fun getCpmRpm(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<ResData<CpmRpmResponse>> {
        return ResData.success(revenueUseCase.getCpmRpm(userId, days))
    }

    @Operation(summary = "브랜드딜 수익 조회", description = "기간 내 브랜드딜 수익을 조회합니다.")
    @GetMapping("/brand-deals")
    fun getBrandDealRevenue(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "90") days: Int,
    ): ResponseEntity<ResData<BrandDealRevenueResponse>> {
        return ResData.success(revenueUseCase.getBrandDealRevenue(userId, days))
    }
}
