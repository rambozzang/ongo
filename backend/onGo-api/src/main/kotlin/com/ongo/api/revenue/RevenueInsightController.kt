package com.ongo.api.revenue

import com.ongo.application.revenue.RevenueInsightUseCase
import com.ongo.application.revenue.dto.RevenueInsightListResponse
import com.ongo.application.revenue.dto.RevenueInsightResponse
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "수익 인사이트", description = "AI 기반 수익 인사이트 조회 및 생성")
@RestController
@RequestMapping("/api/v1/revenue/insights")
class RevenueInsightController(
    private val revenueInsightUseCase: RevenueInsightUseCase,
) {

    @Operation(summary = "수익 인사이트 목록 조회")
    @GetMapping
    fun getInsights(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<ResData<RevenueInsightListResponse>> =
        ResData.success(revenueInsightUseCase.getInsights(userId, page, size))

    @Operation(summary = "AI 수익 인사이트 생성", description = "최근 30일 수익 데이터를 AI로 분석하여 인사이트를 생성합니다. 크레딧 5 차감.")
    @PostMapping("/generate")
    fun generateInsight(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<RevenueInsightResponse>> =
        ResData.success(revenueInsightUseCase.generateInsight(userId))
}
