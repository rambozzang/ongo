package com.ongo.api.qualityscore

import com.ongo.application.qualityscore.QualityScoreUseCase
import com.ongo.application.qualityscore.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "품질 점수", description = "콘텐츠 품질 점수 분석 및 리포트")
@RestController
@RequestMapping("/api/v1/quality-score")
class QualityScoreController(
    private val qualityScoreUseCase: QualityScoreUseCase
) {

    @Operation(summary = "품질 검사 실행")
    @PostMapping("/check")
    fun check(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: QualityCheckRequest,
    ): ResponseEntity<ResData<QualityCheckResponse>> {
        val result = qualityScoreUseCase.check(userId, request)
        return ResData.success(result, "품질 검사가 시작되었습니다")
    }

    @Operation(summary = "품질 검사 이력 조회")
    @GetMapping("/history")
    fun history(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<QualityReportResponse>>> {
        val result = qualityScoreUseCase.history(userId)
        return ResData.success(result)
    }

    @Operation(summary = "품질 리포트 상세 조회")
    @GetMapping("/reports/{id}")
    fun getReport(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<QualityReportResponse>> {
        val result = qualityScoreUseCase.getReport(userId, id)
        return ResData.success(result)
    }
}
