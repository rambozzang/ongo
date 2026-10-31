package com.ongo.api.ugc

import com.ongo.application.ugc.shorts.ShortsSpeakerTrackingUseCase
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import com.ongo.common.annotation.RequiresPermission
import com.ongo.common.enums.Permission
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "UGC 쇼츠 화자 추적", description = "클립 장면을 보고 말하는 사람을 따라가는 세로 크롭을 적용한다")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/ugc/shorts/runs/{runId}/clips/{clipId}/track-speaker")
class ShortsSpeakerTrackingController(
    private val trackingUseCase: ShortsSpeakerTrackingUseCase,
) {

    data class TrackingCostResponse(val credits: Int)

    @Operation(summary = "화자 추적 크레딧", description = "적용하기 전에 보여 줄 크레딧")
    @GetMapping("/cost")
    @RequiresPermission(Permission.SHORTS_PIPELINE_VIEW)
    fun cost(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
        @PathVariable clipId: Long,
    ): ResponseEntity<ResData<TrackingCostResponse>> =
        ResData.success(TrackingCostResponse(trackingUseCase.creditCost()))

    @Operation(
        summary = "화자 추적 적용",
        description = "클립 구간 장면을 보고 말하는 사람을 따라 움직이는 9:16 크롭을 저장한다. 렌더 지시서를 새로 만들고 이전 렌더 결과를 버린다. " +
            "인물을 충분히 찾지 못하거나 실패하면 적용하지 않고 크레딧을 돌려준다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "적용됨"),
        ApiResponse(responseCode = "400", description = "SHORTS_TRACK_NOT_FOUND · SHORTS_CLIP_NOT_EDITABLE · SHORTS_TRACK_FRAMES_FAILED · INSUFFICIENT_CREDIT"),
        ApiResponse(responseCode = "404", description = "실행 또는 클립 없음"),
    )
    @PostMapping
    @RequiresPermission(Permission.SHORTS_PIPELINE_MANAGE)
    fun track(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
        @PathVariable clipId: Long,
    ): ResponseEntity<ResData<ShortsSpeakerTrackingUseCase.TrackingResponse>> =
        ResData.success(trackingUseCase.track(userId, workspaceId, runId, clipId))
}
