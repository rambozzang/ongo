package com.ongo.api.branddeal

import com.ongo.application.branddeal.BrandDealUseCase
import com.ongo.application.branddeal.dto.*
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "브랜드 딜", description = "딜 트래커 + 미디어키트 관리")
@RestController
@RequestMapping("/api/v1/brand-deals")
class BrandDealController(
    private val brandDealUseCase: BrandDealUseCase,
) {

    @Operation(summary = "딜 목록 조회")
    @GetMapping
    fun getDeals(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<BrandDealResponse>>> =
        ResData.success(brandDealUseCase.getDeals(userId, status))

    @Operation(summary = "딜 상세 조회")
    @GetMapping("/{dealId}")
    fun getDeal(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable dealId: Long,
    ): ResponseEntity<ResData<BrandDealResponse>> =
        ResData.success(brandDealUseCase.getDeal(userId, dealId))

    @Operation(summary = "딜 생성")
    @PostMapping
    fun createDeal(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestBody request: CreateBrandDealRequest,
    ): ResponseEntity<ResData<BrandDealResponse>> =
        ResData.success(brandDealUseCase.createDeal(userId, request))

    @Operation(summary = "딜 수정")
    @PutMapping("/{dealId}")
    fun updateDeal(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable dealId: Long,
        @RequestBody request: UpdateBrandDealRequest,
    ): ResponseEntity<ResData<BrandDealResponse>> =
        ResData.success(brandDealUseCase.updateDeal(userId, dealId, request))

    @Operation(summary = "딜 삭제")
    @DeleteMapping("/{dealId}")
    fun deleteDeal(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable dealId: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        brandDealUseCase.deleteDeal(userId, dealId)
        return ResData.success(null, "딜이 삭제되었습니다")
    }

    @Operation(summary = "미디어키트 조회")
    @GetMapping("/media-kit")
    fun getMediaKit(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ResData<MediaKitResponse?>> =
        ResData.success(brandDealUseCase.getMediaKit(userId))

    @Operation(summary = "미디어키트 저장/수정")
    @PutMapping("/media-kit")
    fun saveMediaKit(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestBody request: UpdateMediaKitRequest,
    ): ResponseEntity<ResData<MediaKitResponse>> =
        ResData.success(brandDealUseCase.saveOrUpdateMediaKit(userId, request))

    @Operation(summary = "공개 미디어키트 조회")
    @GetMapping("/media-kit/public/{slug}")
    fun getPublicMediaKit(@PathVariable slug: String): ResponseEntity<ResData<MediaKitResponse?>> =
        ResData.success(brandDealUseCase.getPublicMediaKit(slug))
}
