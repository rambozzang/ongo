package com.ongo.api.fansegmentcampaign

import com.ongo.application.fansegmentcampaign.FanSegmentCampaignUseCase
import com.ongo.application.fansegmentcampaign.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "팬 세그먼트 캠페인", description = "팬 세그먼트별 맞춤 캠페인 관리")
@RestController
@RequestMapping("/api/v1/fan-segment-campaigns")
class FanSegmentCampaignController(
    private val fanSegmentCampaignUseCase: FanSegmentCampaignUseCase
) {

    @Operation(summary = "캠페인 목록 조회")
    @GetMapping
    fun getCampaigns(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<FanCampaignResponse>>> {
        val result = fanSegmentCampaignUseCase.getCampaigns(userId)
        return ResData.success(result)
    }

    @Operation(summary = "캠페인 생성")
    @PostMapping
    fun create(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateCampaignRequest,
    ): ResponseEntity<ResData<FanCampaignResponse>> {
        val result = fanSegmentCampaignUseCase.createCampaign(userId, request)
        return ResData.success(result, "캠페인이 생성되었습니다")
    }

    @Operation(summary = "세그먼트 목록 조회")
    @GetMapping("/segments")
    fun getSegments(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<CampaignSegmentResponse>>> {
        val result = fanSegmentCampaignUseCase.getSegments(userId)
        return ResData.success(result)
    }

    @Operation(summary = "캠페인 삭제")
    @DeleteMapping("/{id}")
    fun delete(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        fanSegmentCampaignUseCase.deleteCampaign(userId, id)
        return ResData.success(null, "캠페인이 삭제되었습니다")
    }

    @Operation(summary = "캠페인 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<FanSegmentCampaignSummaryResponse>> {
        val result = fanSegmentCampaignUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
