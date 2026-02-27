package com.ongo.api.prediction

import com.ongo.application.prediction.PredictionUseCase
import com.ongo.application.prediction.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "성과 예측", description = "AI 기반 콘텐츠 성과 예측")
@RestController
@RequestMapping("/api/v1/predictions")
class PredictionController(
    private val predictionUseCase: PredictionUseCase
) {

    @Operation(summary = "예측 목록 조회")
    @GetMapping
    fun listPredictions(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<PredictionResponse>>> {
        val result = predictionUseCase.listPredictions(userId)
        return ResData.success(result)
    }

    @Operation(summary = "예측 상세 조회")
    @GetMapping("/{id}")
    fun getPrediction(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<PredictionResponse>> {
        val result = predictionUseCase.getPrediction(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "성과 예측 생성")
    @PostMapping
    fun createPrediction(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreatePredictionRequest,
    ): ResponseEntity<ResData<PredictionResponse>> {
        val result = predictionUseCase.createPrediction(userId, request)
        return ResData.success(result, "성과 예측이 생성되었습니다")
    }

    @Operation(summary = "실제 성과 업데이트")
    @PutMapping("/{id}/actuals")
    fun updateActuals(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateActualsRequest,
    ): ResponseEntity<ResData<PredictionResponse>> {
        val result = predictionUseCase.updateActuals(userId, id, request)
        return ResData.success(result)
    }

    @Operation(summary = "예측 삭제")
    @DeleteMapping("/{id}")
    fun deletePrediction(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        predictionUseCase.deletePrediction(userId, id)
        return ResData.success(null, "예측이 삭제되었습니다")
    }

    @Operation(summary = "도달 히트맵 조회")
    @GetMapping("/heatmap")
    fun getReachHeatmap(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) platform: String?,
    ): ResponseEntity<ResData<PredictionHeatmapResponse>> {
        val result = predictionUseCase.getHeatmap(userId, platform)
        return ResData.success(result)
    }

    @Operation(summary = "최적 업로드 시간 추천")
    @GetMapping("/optimal-times")
    fun getOptimalTimes(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) platform: String?,
    ): ResponseEntity<ResData<PredictionOptimalTimesResponse>> {
        val result = predictionUseCase.getOptimalTimes(userId, platform)
        return ResData.success(result)
    }

    @Operation(summary = "제목 제안")
    @GetMapping("/title-suggestions")
    fun getTitleSuggestions(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam title: String,
        @RequestParam(required = false) platform: String?,
    ): ResponseEntity<ResData<TitleSuggestionsResponse>> {
        val result = predictionUseCase.getTitleSuggestions(userId, title, platform)
        return ResData.success(result)
    }

    @Operation(summary = "태그 제안")
    @GetMapping("/tag-suggestions")
    fun getTagSuggestions(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam tags: String,
        @RequestParam(required = false) platform: String?,
    ): ResponseEntity<ResData<TagSuggestionsResponse>> {
        val result = predictionUseCase.getTagSuggestions(userId, tags, platform)
        return ResData.success(result)
    }

    @Operation(summary = "경쟁자 비교 분석")
    @GetMapping("/competitors/{videoId}")
    fun getCompetitorComparison(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable videoId: Long,
    ): ResponseEntity<ResData<CompetitorComparisonResponse>> {
        val result = predictionUseCase.getCompetitorComparison(userId, videoId)
        return ResData.success(result)
    }

    @Operation(summary = "예측 히스토리 조회")
    @GetMapping("/history")
    fun getHistory(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false, defaultValue = "20") limit: Int,
    ): ResponseEntity<ResData<PredictionHistoryResponse>> {
        val result = predictionUseCase.getHistory(userId, limit)
        return ResData.success(result)
    }

    @Operation(summary = "예측 요약 통계")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<PredictionSummaryResponse>> {
        val result = predictionUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
