package com.ongo.api.performancereport

import com.ongo.application.performancereport.PerformanceReportUseCase
import com.ongo.application.performancereport.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "콘텐츠 성과 보고서", description = "종합 성과 리포트 생성 및 관리")
@RestController
@RequestMapping("/api/v1/performance-reports")
class PerformanceReportController(
    private val performanceReportUseCase: PerformanceReportUseCase
) {

    @Operation(summary = "보고서 목록 조회")
    @GetMapping
    fun getReports(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<PerformanceReportResponse>>> {
        val result = performanceReportUseCase.getReports(userId, status)
        return ResData.success(result)
    }

    @Operation(summary = "보고서 섹션 조회")
    @GetMapping("/{reportId}/sections")
    fun getSections(
        @PathVariable reportId: Long,
    ): ResponseEntity<ResData<List<ReportSectionResponse>>> {
        val result = performanceReportUseCase.getSections(reportId)
        return ResData.success(result)
    }

    @Operation(summary = "보고서 생성")
    @PostMapping
    fun generate(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: GenerateReportRequest,
    ): ResponseEntity<ResData<PerformanceReportResponse>> {
        val result = performanceReportUseCase.generate(userId, request)
        return ResData.success(result, "보고서 생성이 시작되었습니다")
    }

    @Operation(summary = "보고서 삭제")
    @DeleteMapping("/{id}")
    fun deleteReport(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        performanceReportUseCase.deleteReport(userId, id)
        return ResData.success(null, "보고서가 삭제되었습니다")
    }

    @Operation(summary = "보고서 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<PerformanceReportSummaryResponse>> {
        val result = performanceReportUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
