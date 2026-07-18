package com.ongo.api.ugc

import com.ongo.application.ugc.ParticipationUseCase
import com.ongo.application.ugc.dto.ApplicationResponse
import com.ongo.application.ugc.dto.ApplyRequest
import com.ongo.application.ugc.dto.MyApplicationListResponse
import com.ongo.application.ugc.dto.PublicCampaignResponse
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "UGC 모집·참여 (크리에이터)", description = "초대 링크로 캠페인 조회·지원, 내 지원 목록")
@RestController
@RequestMapping("/api/v1/ugc")
class CreatorParticipationController(
    private val participationUseCase: ParticipationUseCase,
) {

    @Operation(summary = "초대 링크로 캠페인 조회")
    @GetMapping("/invites/{token}")
    fun viewInvite(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable token: String,
    ): ResponseEntity<ResData<PublicCampaignResponse>> =
        ResData.success(participationUseCase.getCampaignByToken(userId, token))

    @Operation(summary = "캠페인 지원")
    @PostMapping("/invites/{token}/applications")
    fun apply(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable token: String,
        @RequestBody request: ApplyRequest,
    ): ResponseEntity<ResData<ApplicationResponse>> =
        ResData.success(participationUseCase.apply(userId, token, request), "지원이 완료되었습니다")

    @Operation(summary = "내 지원 목록")
    @GetMapping("/me/applications")
    fun myApplications(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<MyApplicationListResponse>> =
        ResData.success(participationUseCase.myApplications(userId, page, size))
}
