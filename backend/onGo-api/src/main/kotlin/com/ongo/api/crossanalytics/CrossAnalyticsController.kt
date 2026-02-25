package com.ongo.api.crossanalytics

import com.ongo.application.crossanalytics.CrossAnalyticsUseCase
import com.ongo.application.crossanalytics.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "크로스 분석", description = "크로스 플랫폼 분석 리포트 생성 및 조회")
@RestController
@RequestMapping("/api/v1/cross-analytics")
class CrossAnalyticsController(
    private val crossAnalyticsUseCase: CrossAnalyticsUseCase
) {

    @Operation(summary = "크로스 플랫폼 리포트 목록 조회")
    @GetMapping("/reports")
    fun listReports(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<CrossPlatformReportResponse>>> {
        val result = crossAnalyticsUseCase.listReports(userId)
        return ResData.success(result)
    }

    @Operation(summary = "크로스 플랫폼 리포트 상세 조회")
    @GetMapping("/reports/{id}")
    fun getReport(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<CrossPlatformReportResponse>> {
        val result = crossAnalyticsUseCase.getReport(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "크로스 플랫폼 리포트 생성")
    @PostMapping("/reports")
    fun generateReport(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: GenerateReportRequest,
    ): ResponseEntity<ResData<CrossPlatformReportResponse>> {
        val result = crossAnalyticsUseCase.generateReport(userId, request)
        return ResData.success(result, "크로스 플랫폼 리포트가 생성되었습니다")
    }
}
