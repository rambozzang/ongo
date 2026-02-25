package com.ongo.api.competitoranalysis

import com.ongo.application.competitoranalysis.CompetitorAnalysisUseCase
import com.ongo.application.competitoranalysis.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "경쟁자 분석", description = "경쟁자 프로필 및 분석 리포트 관리")
@RestController
@RequestMapping("/api/v1/competitor-analysis")
class CompetitorAnalysisController(
    private val competitorAnalysisUseCase: CompetitorAnalysisUseCase
) {

    @Operation(summary = "경쟁자 프로필 목록 조회")
    @GetMapping("/profiles")
    fun listProfiles(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<CompetitorProfileResponse>>> {
        val result = competitorAnalysisUseCase.listProfiles(userId)
        return ResData.success(result)
    }

    @Operation(summary = "경쟁자 프로필 추가")
    @PostMapping("/profiles")
    fun createProfile(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateCompetitorProfileRequest,
    ): ResponseEntity<ResData<CompetitorProfileResponse>> {
        val result = competitorAnalysisUseCase.createProfile(userId, request)
        return ResData.success(result, "경쟁자 프로필이 추가되었습니다")
    }

    @Operation(summary = "경쟁자 프로필 삭제")
    @DeleteMapping("/profiles/{id}")
    fun deleteProfile(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        competitorAnalysisUseCase.deleteProfile(userId, id)
        return ResData.success(null, "경쟁자 프로필이 삭제되었습니다")
    }

    @Operation(summary = "분석 리포트 목록 조회")
    @GetMapping("/reports")
    fun listReports(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<CompetitorReportResponse>>> {
        val result = competitorAnalysisUseCase.listReports(userId)
        return ResData.success(result)
    }

    @Operation(summary = "분석 리포트 생성")
    @PostMapping("/reports")
    fun createReport(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateCompetitorReportRequest,
    ): ResponseEntity<ResData<CompetitorReportResponse>> {
        val result = competitorAnalysisUseCase.createReport(userId, request)
        return ResData.success(result, "분석 리포트가 생성되었습니다")
    }

    @Operation(summary = "분석 리포트 삭제")
    @DeleteMapping("/reports/{id}")
    fun deleteReport(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        competitorAnalysisUseCase.deleteReport(userId, id)
        return ResData.success(null, "분석 리포트가 삭제되었습니다")
    }
}
