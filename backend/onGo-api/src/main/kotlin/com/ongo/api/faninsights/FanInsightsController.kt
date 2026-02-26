package com.ongo.api.faninsights

import com.ongo.application.faninsights.FanInsightsUseCase
import com.ongo.application.faninsights.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "팬 인사이트", description = "팬 행동 패턴 및 인구 통계 분석")
@RestController
@RequestMapping("/api/v1/fan-insights")
class FanInsightsController(
    private val useCase: FanInsightsUseCase
) {

    @Operation(summary = "팬 인구통계 조회")
    @GetMapping("/demographics")
    fun getDemographics(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) platform: String?,
    ): ResponseEntity<ResData<List<FanDemographicResponse>>> {
        val result = useCase.getDemographics(userId, platform)
        return ResData.success(result)
    }

    @Operation(summary = "팬 행동 패턴 조회")
    @GetMapping("/behaviors")
    fun getBehaviors(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) platform: String?,
    ): ResponseEntity<ResData<List<FanBehaviorResponse>>> {
        val result = useCase.getBehaviors(userId, platform)
        return ResData.success(result)
    }

    @Operation(summary = "팬 세그먼트 조회")
    @GetMapping("/segments")
    fun getSegments(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<FanSegmentResponse>>> {
        val result = useCase.getSegments(userId)
        return ResData.success(result)
    }

    @Operation(summary = "팬 인사이트 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<FanInsightsSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
