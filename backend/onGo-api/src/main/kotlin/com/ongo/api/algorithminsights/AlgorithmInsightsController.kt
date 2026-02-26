package com.ongo.api.algorithminsights

import com.ongo.application.algorithminsights.AlgorithmInsightsUseCase
import com.ongo.application.algorithminsights.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "알고리즘 인사이트", description = "플랫폼 알고리즘 점수 분석 및 변경 추적")
@RestController
@RequestMapping("/api/v1/algorithm-insights")
class AlgorithmInsightsController(
    private val useCase: AlgorithmInsightsUseCase
) {

    @Operation(summary = "알고리즘 인사이트 목록 조회")
    @GetMapping
    fun getInsights(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) platform: String?,
    ): ResponseEntity<ResData<List<AlgorithmInsightResponse>>> {
        val result = useCase.getInsights(userId, platform)
        return ResData.success(result)
    }

    @Operation(summary = "플랫폼별 알고리즘 점수 목록 조회")
    @GetMapping("/scores")
    fun getScores(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<AlgorithmScoreResponse>>> {
        val result = useCase.getScores(userId)
        return ResData.success(result)
    }

    @Operation(summary = "특정 플랫폼 알고리즘 점수 조회")
    @GetMapping("/scores/{platform}")
    fun getScore(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable platform: String,
    ): ResponseEntity<ResData<AlgorithmScoreResponse?>> {
        val result = useCase.getScore(userId, platform)
        return ResData.success(result)
    }

    @Operation(summary = "알고리즘 변경 이력 조회")
    @GetMapping("/changes")
    fun getChanges(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<AlgorithmChangeResponse>>> {
        val result = useCase.getChanges(userId)
        return ResData.success(result)
    }

    @Operation(summary = "알고리즘 인사이트 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<AlgorithmInsightsSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
