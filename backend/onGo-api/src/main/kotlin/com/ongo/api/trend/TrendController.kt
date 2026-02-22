package com.ongo.api.trend

import com.ongo.application.trend.TrendUseCase
import com.ongo.application.trend.dto.*
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@Tag(name = "트렌드 모니터링", description = "키워드 트렌드 조회, AI 분석, 알림 관리")
@RestController
@RequestMapping("/api/v1/trends")
class TrendController(
    private val trendUseCase: TrendUseCase,
) {

    @Operation(summary = "트렌드 목록 조회", description = "날짜/카테고리/출처별 트렌드 키워드를 조회합니다.")
    @GetMapping
    fun getTrends(
        @RequestParam(required = false) date: LocalDate?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) source: String?,
    ): ResponseEntity<ResData<List<TrendResponse>>> =
        ResData.success(trendUseCase.getTrends(date, category, source))

    @Operation(summary = "트렌드 검색", description = "키워드로 트렌드를 검색합니다.")
    @GetMapping("/search")
    fun searchTrends(
        @RequestParam keyword: String,
    ): ResponseEntity<ResData<List<TrendResponse>>> =
        ResData.success(trendUseCase.searchTrends(keyword))

    @Operation(summary = "AI 트렌드 분석", description = "현재 트렌드를 AI로 분석하여 콘텐츠 추천을 생성합니다. (5 크레딧)")
    @GetMapping("/analysis")
    fun analyzeTrends(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestParam(required = false) category: String?,
    ): ResponseEntity<ResData<TrendAnalysisResponse>> =
        ResData.success(trendUseCase.analyzeTrends(userId, category))

    @Operation(summary = "트렌드 알림 목록", description = "설정된 트렌드 알림 목록을 조회합니다.")
    @GetMapping("/alerts")
    fun getAlerts(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ResData<List<TrendAlertResponse>>> =
        ResData.success(trendUseCase.getAlerts(userId))

    @Operation(summary = "트렌드 알림 생성", description = "특정 키워드에 대한 트렌드 알림을 설정합니다.")
    @PostMapping("/alerts")
    fun createAlert(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestBody request: CreateTrendAlertRequest,
    ): ResponseEntity<ResData<TrendAlertResponse>> =
        ResData.success(trendUseCase.createAlert(userId, request))

    @Operation(summary = "트렌드 알림 수정", description = "트렌드 알림 설정을 수정합니다.")
    @PutMapping("/alerts/{alertId}")
    fun updateAlert(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable alertId: Long,
        @RequestBody request: UpdateTrendAlertRequest,
    ): ResponseEntity<ResData<TrendAlertResponse>> =
        ResData.success(trendUseCase.updateAlert(userId, alertId, request))

    @Operation(summary = "트렌드 알림 삭제", description = "트렌드 알림을 삭제합니다.")
    @DeleteMapping("/alerts/{alertId}")
    fun deleteAlert(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable alertId: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        trendUseCase.deleteAlert(userId, alertId)
        return ResData.success(null, "알림이 삭제되었습니다")
    }
}
