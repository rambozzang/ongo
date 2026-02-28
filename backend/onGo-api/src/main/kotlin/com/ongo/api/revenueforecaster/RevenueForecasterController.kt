package com.ongo.api.revenueforecaster

import com.ongo.application.revenueforecaster.RevenueForecasterUseCase
import com.ongo.application.revenueforecaster.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "수익 예측기", description = "AI 기반 수익 예측 및 시나리오 분석")
@RestController
@RequestMapping("/api/v1/revenue-forecaster")
class RevenueForecasterController(
    private val useCase: RevenueForecasterUseCase
) {

    @Operation(summary = "수익 예측 조회")
    @GetMapping
    fun getForecast(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) period: String?,
    ): ResponseEntity<ResData<RevenueForecastResponse>> {
        val result = useCase.getForecast(userId, period)
        return ResData.success(result)
    }

    @Operation(summary = "수익 예측 생성")
    @PostMapping("/generate")
    fun generateForecast(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: ForecastRequest,
    ): ResponseEntity<ResData<ForecastGenerateResponse>> {
        val result = useCase.generateForecast(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "수익 예측 이력 조회")
    @GetMapping("/history")
    fun getHistory(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<RevenueForecastHistoryWrapper>> {
        val result = useCase.getHistory(userId)
        return ResData.success(result)
    }
}
