package com.ongo.api.trendpredictor

import com.ongo.application.trendpredictor.TrendPredictorUseCase
import com.ongo.application.trendpredictor.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "AI 트렌드 예측기", description = "AI 기반 콘텐츠 트렌드 예측")
@RestController
@RequestMapping("/api/v1/trend-predictor")
class TrendPredictorController(
    private val trendPredictorUseCase: TrendPredictorUseCase
) {

    @Operation(summary = "트렌드 예측 목록 조회")
    @GetMapping
    fun getPredictions(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) category: String?,
    ): ResponseEntity<ResData<List<TrendPredictionResponse>>> {
        val result = trendPredictorUseCase.getPredictions(userId, category)
        return ResData.success(result)
    }

    @Operation(summary = "트렌드 토픽 조회")
    @GetMapping("/{predictionId}/topics")
    fun getTopics(
        @PathVariable predictionId: Long,
    ): ResponseEntity<ResData<List<TrendTopicResponse>>> {
        val result = trendPredictorUseCase.getTopics(predictionId)
        return ResData.success(result)
    }

    @Operation(summary = "트렌드 예측 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<TrendPredictorSummaryResponse>> {
        val result = trendPredictorUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
