package com.ongo.api.platformhealth

import com.ongo.application.platformhealth.PlatformHealthUseCase
import com.ongo.application.platformhealth.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "플랫폼 건강 점수", description = "각 플랫폼별 채널 건강 상태 분석")
@RestController
@RequestMapping("/api/v1/platform-health")
class PlatformHealthController(
    private val useCase: PlatformHealthUseCase
) {

    @Operation(summary = "건강 점수 목록 조회")
    @GetMapping
    fun getScores(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<PlatformHealthScoreResponse>>> {
        val result = useCase.getScores(userId)
        return ResData.success(result)
    }

    @Operation(summary = "플랫폼별 건강 점수 조회")
    @GetMapping("/{platform}")
    fun getScore(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable platform: String,
    ): ResponseEntity<ResData<PlatformHealthScoreResponse?>> {
        val result = useCase.getScore(userId, platform)
        return ResData.success(result)
    }

    @Operation(summary = "건강 이슈 목록 조회")
    @GetMapping("/{healthScoreId}/issues")
    fun getIssues(
        @PathVariable healthScoreId: Long,
    ): ResponseEntity<ResData<List<HealthIssueResponse>>> {
        val result = useCase.getIssues(healthScoreId)
        return ResData.success(result)
    }

    @Operation(summary = "건강 점수 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<PlatformHealthSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
