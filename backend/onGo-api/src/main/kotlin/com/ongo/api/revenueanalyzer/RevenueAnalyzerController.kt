package com.ongo.api.revenueanalyzer

import com.ongo.application.revenueanalyzer.RevenueAnalyzerUseCase
import com.ongo.application.revenueanalyzer.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "수익 분석기", description = "수익원별 상세 분석 및 예측")
@RestController
@RequestMapping("/api/v1/revenue-analyzer")
class RevenueAnalyzerController(
    private val revenueAnalyzerUseCase: RevenueAnalyzerUseCase
) {

    @Operation(summary = "수익 스트림 목록 조회")
    @GetMapping
    fun getStreams(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<RevenueStreamResponse>>> {
        val result = revenueAnalyzerUseCase.getStreams(userId)
        return ResData.success(result)
    }

    @Operation(summary = "수익 예측 조회")
    @GetMapping("/{channelId}/projections")
    fun getProjections(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable channelId: Long,
    ): ResponseEntity<ResData<List<RevenueProjectionResponse>>> {
        val result = revenueAnalyzerUseCase.getProjections(userId, channelId)
        return ResData.success(result)
    }

    @Operation(summary = "수익 분석 실행")
    @PostMapping("/{channelId}/analyze")
    fun analyze(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable channelId: Long,
    ): ResponseEntity<ResData<List<RevenueStreamResponse>>> {
        val result = revenueAnalyzerUseCase.analyze(userId, channelId)
        return ResData.success(result)
    }

    @Operation(summary = "수익 분석 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<RevenueAnalyzerSummaryResponse>> {
        val result = revenueAnalyzerUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
