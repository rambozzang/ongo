package com.ongo.api.ugc

import com.ongo.application.ugc.RewardUseCase
import com.ongo.application.ugc.dto.ParticipantRewardListResponse
import com.ongo.application.ugc.dto.ParticipantRewardResponse
import com.ongo.application.ugc.dto.UpdateRewardRequest
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "UGC 보상 (브랜드)", description = "참여자 보상 확정·지급 표시·CSV 내보내기")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/ugc")
class CampaignRewardController(
    private val rewardUseCase: RewardUseCase,
) {

    @Operation(summary = "참여자 보상 목록", description = "참여자별 보상과 예산 대비 확정 총액을 반환합니다.")
    @GetMapping("/campaigns/{campaignId}/participants")
    fun participants(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable campaignId: Long,
    ): ResponseEntity<ResData<ParticipantRewardListResponse>> =
        ResData.success(rewardUseCase.listParticipantRewards(userId, workspaceId, campaignId))

    @Operation(summary = "보상 금액 수정 (DRAFT)")
    @PutMapping("/participants/{participantId}/reward")
    fun updateReward(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable participantId: Long,
        @RequestBody request: UpdateRewardRequest,
    ): ResponseEntity<ResData<ParticipantRewardResponse>> =
        ResData.success(rewardUseCase.updateReward(userId, workspaceId, participantId, request))

    @Operation(summary = "보상 확정", description = "확정 총액이 예산을 초과하면 차단됩니다.")
    @PostMapping("/participants/{participantId}/reward/confirm")
    fun confirmReward(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable participantId: Long,
    ): ResponseEntity<ResData<ParticipantRewardResponse>> =
        ResData.success(rewardUseCase.confirmReward(userId, workspaceId, participantId), "보상을 확정했습니다")

    @Operation(summary = "지급 완료 표시")
    @PostMapping("/participants/{participantId}/reward/mark-paid")
    fun markPaid(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable participantId: Long,
    ): ResponseEntity<ResData<ParticipantRewardResponse>> =
        ResData.success(rewardUseCase.markPaid(userId, workspaceId, participantId), "지급 완료로 표시했습니다")

    @Operation(summary = "지급 대상 CSV 내보내기")
    @GetMapping("/campaigns/{campaignId}/rewards.csv")
    fun exportCsv(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable campaignId: Long,
    ): ResponseEntity<ByteArray> {
        val csv = rewardUseCase.exportRewardsCsv(userId, workspaceId, campaignId)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"campaign-$campaignId-rewards.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv)
    }
}
