package com.ongo.api.sponsorship

import com.ongo.application.sponsorship.SponsorshipUseCase
import com.ongo.application.sponsorship.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "스폰서십 트래커", description = "스폰서십 딜 및 산출물 추적 관리")
@RestController
@RequestMapping("/api/v1/sponsorships")
class SponsorshipController(
    private val sponsorshipUseCase: SponsorshipUseCase
) {

    @Operation(summary = "스폰서십 목록 조회")
    @GetMapping
    fun getSponsorships(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<SponsorshipResponse>>> {
        val result = sponsorshipUseCase.getSponsorships(userId, status)
        return ResData.success(result)
    }

    @Operation(summary = "스폰서십 상세 조회")
    @GetMapping("/{id}")
    fun getSponsorship(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<SponsorshipResponse>> {
        val result = sponsorshipUseCase.getSponsorship(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "스폰서십 생성")
    @PostMapping
    fun createSponsorship(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateSponsorshipRequest,
    ): ResponseEntity<ResData<SponsorshipResponse>> {
        val result = sponsorshipUseCase.createSponsorship(userId, request)
        return ResData.success(result, "스폰서십이 생성되었습니다")
    }

    @Operation(summary = "스폰서십 수정")
    @PutMapping("/{id}")
    fun updateSponsorship(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateSponsorshipRequest,
    ): ResponseEntity<ResData<SponsorshipResponse>> {
        val result = sponsorshipUseCase.updateSponsorship(userId, id, request)
        return ResData.success(result, "스폰서십이 수정되었습니다")
    }

    @Operation(summary = "스폰서십 삭제")
    @DeleteMapping("/{id}")
    fun deleteSponsorship(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        sponsorshipUseCase.deleteSponsorship(userId, id)
        return ResData.success(null, "스폰서십이 삭제되었습니다")
    }

    @Operation(summary = "스폰서십 요약 조회")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<SponsorshipSummaryResponse>> {
        val result = sponsorshipUseCase.getSummary(userId)
        return ResData.success(result)
    }

    @Operation(summary = "산출물 생성")
    @PostMapping("/deliverables")
    fun createDeliverable(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateDeliverableRequest,
    ): ResponseEntity<ResData<DeliverableResponse>> {
        val result = sponsorshipUseCase.createDeliverable(userId, request)
        return ResData.success(result, "산출물이 생성되었습니다")
    }

    @Operation(summary = "산출물 완료 처리")
    @PutMapping("/deliverables/{id}/complete")
    fun completeDeliverable(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<DeliverableResponse>> {
        val result = sponsorshipUseCase.completeDeliverable(userId, id)
        return ResData.success(result, "산출물이 완료 처리되었습니다")
    }

    @Operation(summary = "산출물 삭제")
    @DeleteMapping("/deliverables/{id}")
    fun deleteDeliverable(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        sponsorshipUseCase.deleteDeliverable(userId, id)
        return ResData.success(null, "산출물이 삭제되었습니다")
    }
}
