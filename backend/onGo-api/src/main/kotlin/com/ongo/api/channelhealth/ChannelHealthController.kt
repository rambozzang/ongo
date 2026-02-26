package com.ongo.api.channelhealth

import com.ongo.application.channelhealth.ChannelHealthUseCase
import com.ongo.application.channelhealth.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "채널 건강도 대시보드", description = "채널 종합 건강도 지표 분석")
@RestController
@RequestMapping("/api/v1/channel-health")
class ChannelHealthController(
    private val channelHealthUseCase: ChannelHealthUseCase
) {

    @Operation(summary = "건강도 지표 목록 조회")
    @GetMapping
    fun getMetrics(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<ChannelHealthMetricResponse>>> {
        val result = channelHealthUseCase.getMetrics(userId)
        return ResData.success(result)
    }

    @Operation(summary = "채널 건강도 측정")
    @PostMapping("/{channelId}/measure")
    fun measure(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable channelId: Long,
    ): ResponseEntity<ResData<ChannelHealthMetricResponse>> {
        val result = channelHealthUseCase.measure(userId, channelId)
        return ResData.success(result, "채널 건강도가 측정되었습니다")
    }

    @Operation(summary = "건강도 트렌드 조회")
    @GetMapping("/{metricId}/trends")
    fun getTrends(
        @PathVariable metricId: Long,
    ): ResponseEntity<ResData<List<HealthTrendResponse>>> {
        val result = channelHealthUseCase.getTrends(metricId)
        return ResData.success(result)
    }

    @Operation(summary = "채널 건강도 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<ChannelHealthSummaryResponse>> {
        val result = channelHealthUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
