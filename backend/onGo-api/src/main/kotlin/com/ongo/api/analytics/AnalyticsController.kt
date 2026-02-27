package com.ongo.api.analytics

import com.ongo.application.analytics.dto.*
import com.ongo.api.config.CurrentUser
import com.ongo.application.analytics.AnalyticsUseCase
import com.ongo.application.analytics.CohortAnalysisUseCase
import com.ongo.application.analytics.CohortGroupBy
import com.ongo.application.analytics.LiveDashboardUseCase
import com.ongo.application.analytics.PerformanceScoreUseCase
import com.ongo.common.ResData
import com.ongo.common.annotation.RequiresPermission
import com.ongo.common.enums.Permission
import com.ongo.common.enums.Platform
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "분석", description = "대시보드 KPI, 트렌드, 영상별 분석, 히트맵, 플랫폼 비교")
@RestController
@RequestMapping("/api/v1/analytics")
class AnalyticsController(
    private val analyticsUseCase: AnalyticsUseCase,
    private val performanceScoreUseCase: PerformanceScoreUseCase,
    private val cohortAnalysisUseCase: CohortAnalysisUseCase,
    private val liveDashboardUseCase: LiveDashboardUseCase,
) {

    @Operation(
        summary = "대시보드 KPI 조회",
        description = "총 조회수, 구독자 수, 좋아요 수, 댓글 수 등 주요 KPI 지표를 조회합니다. 이전 기간 대비 변화율이 포함됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "대시보드 KPI 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @RequiresPermission(Permission.ANALYTICS_READ)
    @GetMapping("/dashboard")
    fun getDashboardKpi(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "조회 기간 (일 수, 기본값: 7)") @RequestParam(defaultValue = "7") days: Int
    ): ResponseEntity<ResData<DashboardKpiResponse>> {
        return ResData.success(analyticsUseCase.getDashboardKpi(userId, days))
    }

    @Operation(
        summary = "트렌드 데이터 조회",
        description = "지정된 기간 동안의 일별 조회수, 좋아요 수, 댓글 수 등의 트렌드 데이터를 조회합니다. 차트 렌더링에 사용됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "트렌드 데이터 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @RequiresPermission(Permission.ANALYTICS_READ)
    @GetMapping("/trends")
    fun getTrends(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "조회 기간 (일 수, 기본값: 7)") @RequestParam(defaultValue = "7") days: Int
    ): ResponseEntity<ResData<TrendDataResponse>> {
        return ResData.success(analyticsUseCase.getTrends(userId, days))
    }

    @Operation(
        summary = "영상별 분석 조회",
        description = "특정 영상의 상세 분석 데이터를 조회합니다. 플랫폼별 조회수, 시청 시간, 참여율 등이 포함됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "영상별 분석 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @GetMapping("/videos/{id}")
    fun getVideoAnalytics(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "분석할 영상 ID") @PathVariable id: Long,
        @Parameter(description = "조회 기간 (일 수, 기본값: 7)") @RequestParam(defaultValue = "7") days: Int
    ): ResponseEntity<ResData<VideoAnalyticsResponse>> {
        return ResData.success(analyticsUseCase.getVideoAnalytics(userId, id, days))
    }

    @Operation(
        summary = "히트맵 조회",
        description = "요일별/시간대별 게시 성과 히트맵 데이터를 조회합니다. 최적의 게시 시간대를 파악하는 데 활용됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "히트맵 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @GetMapping("/heatmap")
    fun getHeatmap(
        @Parameter(hidden = true) @CurrentUser userId: Long
    ): ResponseEntity<ResData<HeatmapResponse>> {
        return ResData.success(analyticsUseCase.getHeatmap(userId))
    }

    @Operation(
        summary = "인기 영상 조회",
        description = "지정된 기간 동안 조회수 기준 인기 영상 목록을 조회합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "인기 영상 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @GetMapping("/top-videos")
    fun getTopVideos(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "조회 기간 (일 수, 기본값: 7)") @RequestParam(defaultValue = "7") days: Int,
        @Parameter(description = "조회할 영상 수 (기본값: 10)") @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ResData<TopVideoResponse>> {
        return ResData.success(analyticsUseCase.getTopVideos(userId, days, limit))
    }

    @Operation(
        summary = "플랫폼 비교 조회",
        description = "각 플랫폼(YouTube, TikTok, Instagram, Naver Clip)별 성과를 비교 분석합니다. 조회수, 참여율 등의 지표가 플랫폼별로 제공됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "플랫폼 비교 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @GetMapping("/platform-comparison")
    fun getPlatformComparison(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "조회 기간 (일 수, 기본값: 7)") @RequestParam(defaultValue = "7") days: Int
    ): ResponseEntity<ResData<PlatformComparisonResponse>> {
        return ResData.success(analyticsUseCase.getPlatformComparison(userId, days))
    }

    @Operation(
        summary = "최적 게시 시간 조회",
        description = "분석 데이터를 기반으로 최적의 게시 시간 상위 5개를 계산합니다. 최근 30일 데이터에 3배 가중치를 적용합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "최적 게시 시간 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @GetMapping("/optimal-times")
    fun getOptimalPublishTimes(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "플랫폼 필터 (선택)") @RequestParam(required = false) platform: Platform?
    ): ResponseEntity<ResData<OptimalTimesResponse>> {
        return ResData.success(analyticsUseCase.getOptimalPublishTimes(userId, platform))
    }

    @Operation(
        summary = "태그 퍼포먼스 조회",
        description = "사용자 영상에 사용된 태그별 조회수, 좋아요, 참여율, 트렌드 등의 성과를 분석합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "태그 퍼포먼스 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @RequiresPermission(Permission.ANALYTICS_READ)
    @GetMapping("/tags")
    fun getTagPerformance(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "조회 기간 (일 수, 기본값: 30)") @RequestParam(defaultValue = "30") days: Int
    ): ResponseEntity<ResData<TagPerformanceResponse>> {
        return ResData.success(analyticsUseCase.getTagPerformance(userId, days))
    }

    @Operation(
        summary = "영상 성과 점수 조회",
        description = "특정 영상의 복합 성과 점수(0-100)를 조회합니다. 조회수 속도, 참여율, 시청시간, 구독자 전환율, 공유율의 가중 합산 점수와 이상 감지 결과가 포함됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "성과 점수 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @GetMapping("/videos/{id}/performance-score")
    fun getPerformanceScore(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "영상 ID") @PathVariable id: Long,
    ): ResponseEntity<ResData<PerformanceScoreResponse>> {
        return ResData.success(performanceScoreUseCase.getPerformanceScore(userId, id))
    }

    @Operation(
        summary = "이상 감지 목록 조회",
        description = "채널 내 성과 이상(바이럴 스파이크, 참여율 급등, 비정상 하락 등)이 감지된 영상 목록을 조회합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "이상 감지 목록 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @GetMapping("/anomalies")
    fun getAnomalies(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<AnomalyListResponse>> {
        return ResData.success(performanceScoreUseCase.getAnomalies(userId))
    }

    @Operation(
        summary = "영상 비교 분석",
        description = "여러 영상의 성과 지표를 나란히 비교 분석합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "영상 비교 분석 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @GetMapping("/compare")
    fun getVideoComparison(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "비교할 영상 ID 목록") @RequestParam videoIds: List<Long>,
        @Parameter(description = "조회 기간 (예: 30d)") @RequestParam(defaultValue = "30") days: Int
    ): ResponseEntity<ResData<VideoCompareResponse>> {
        return ResData.success(analyticsUseCase.getVideoComparison(userId, videoIds, days))
    }

    @Operation(
        summary = "코호트 분석",
        description = "카테고리, 태그, 플랫폼, 업로드 월 기준으로 영상을 그룹핑하고 누적 조회수 커브를 분석합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "코호트 분석 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @RequiresPermission(Permission.ANALYTICS_READ)
    @GetMapping("/cohort")
    fun getCohortAnalysis(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "그룹 기준 (CATEGORY, TAG, PLATFORM, UPLOAD_MONTH)") @RequestParam(defaultValue = "CATEGORY") groupBy: String,
        @Parameter(description = "조회 시작일 (ISO 8601)") @RequestParam(required = false) from: java.time.LocalDate?,
        @Parameter(description = "조회 종료일 (ISO 8601)") @RequestParam(required = false) to: java.time.LocalDate?,
    ): ResponseEntity<ResData<CohortAnalysisResponse>> {
        val groupByEnum = try {
            CohortGroupBy.valueOf(groupBy.uppercase())
        } catch (_: IllegalArgumentException) {
            CohortGroupBy.CATEGORY
        }
        return ResData.success(cohortAnalysisUseCase.getCohortAnalysis(userId, groupByEnum, from, to))
    }

    @Operation(
        summary = "시청자 리텐션 커브",
        description = "특정 영상의 시청자 리텐션 커브를 조회합니다. 채널 평균 대비 비교 및 이탈 구간 분석이 포함됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "리텐션 커브 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @RequiresPermission(Permission.ANALYTICS_READ)
    @GetMapping("/videos/{id}/retention")
    fun getRetentionCurve(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "영상 ID") @PathVariable id: Long,
    ): ResponseEntity<ResData<RetentionCurveResponse>> {
        return ResData.success(cohortAnalysisUseCase.getRetentionCurve(userId, id))
    }

    @Operation(summary = "트래픽 소스 조회")
    @RequiresPermission(Permission.ANALYTICS_READ)
    @GetMapping("/traffic-sources")
    fun getTrafficSources(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<ResData<TrafficSourceResponse>> =
        ResData.success(analyticsUseCase.getTrafficSources(userId, days))

    @Operation(summary = "시청자 인구통계 조회")
    @RequiresPermission(Permission.ANALYTICS_READ)
    @GetMapping("/demographics")
    fun getDemographics(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<ResData<DemographicsResponse>> =
        ResData.success(analyticsUseCase.getDemographics(userId, days))

    @Operation(summary = "CTR 트렌드 조회")
    @RequiresPermission(Permission.ANALYTICS_READ)
    @GetMapping("/ctr")
    fun getCTRTrend(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<ResData<CTRResponse>> =
        ResData.success(analyticsUseCase.getCTRTrend(userId, days))

    @Operation(summary = "평균 시청 시간 트렌드")
    @RequiresPermission(Permission.ANALYTICS_READ)
    @GetMapping("/avg-view-duration")
    fun getAvgViewDuration(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<ResData<AvgViewDurationResponse>> =
        ResData.success(analyticsUseCase.getAvgViewDuration(userId, days))

    @Operation(
        summary = "크로스 플랫폼 성과 비교",
        description = "동일 영상이 업로드된 여러 플랫폼의 성과를 비교 분석합니다. 영상별 플랫폼 지표, 순위, 인사이트가 포함됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "크로스 플랫폼 비교 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @RequiresPermission(Permission.ANALYTICS_READ)
    @GetMapping("/cross-platform")
    fun getCrossPlatformComparison(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "조회 기간 (일 수, 기본값: 30)") @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<ResData<CrossPlatformSummaryResponse>> =
        ResData.success(analyticsUseCase.getCrossPlatformComparison(userId, days))

    @Operation(summary = "구독 전환 분석")
    @RequiresPermission(Permission.ANALYTICS_READ)
    @GetMapping("/subscriber-conversion")
    fun getSubscriberConversion(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<ResData<SubscriberConversionResponse>> =
        ResData.success(analyticsUseCase.getSubscriberConversion(userId, days))

    // ── 라이브 대시보드 ─────────────────────────────────────────────────

    @Operation(summary = "라이브 대시보드 현황 조회")
    @GetMapping("/live")
    fun getLiveDashboard(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<LiveDashboardStateResponse>> {
        val result = liveDashboardUseCase.getLiveState(userId)
        return ResData.success(result)
    }

    @Operation(summary = "라이브 알림 목록 조회")
    @GetMapping("/live/alerts")
    fun getLiveAlerts(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<Map<String, List<LiveAlertResponse>>>> {
        val alerts = liveDashboardUseCase.getAlerts(userId)
        return ResData.success(mapOf("alerts" to alerts))
    }

    @Operation(summary = "라이브 알림 읽음 처리")
    @PutMapping("/live/alerts/{alertId}/read")
    fun markLiveAlertRead(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable alertId: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        liveDashboardUseCase.markAlertRead(userId, alertId)
        return ResData.success(null, "알림이 읽음 처리되었습니다")
    }

    @Operation(summary = "라이브 알림 설정 조회")
    @GetMapping("/live/alert-configs")
    fun getLiveAlertConfigs(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<Map<String, List<LiveAlertConfigResponse>>>> {
        val configs = liveDashboardUseCase.getAlertConfigs(userId)
        return ResData.success(mapOf("configs" to configs))
    }

    @Operation(summary = "라이브 알림 설정 수정")
    @PutMapping("/live/alert-configs")
    fun updateLiveAlertConfig(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: UpdateLiveAlertConfigRequest,
    ): ResponseEntity<ResData<LiveAlertConfigResponse>> {
        val result = liveDashboardUseCase.updateAlertConfig(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "히트맵 기반 추천 조회")
    @GetMapping("/heatmap/recommendations")
    fun getHeatmapRecommendations(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<HeatmapRecommendationResponse>>> {
        val result = liveDashboardUseCase.getHeatmapRecommendations(userId)
        return ResData.success(result)
    }
}
