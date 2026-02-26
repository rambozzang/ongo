package com.ongo.api.creatormarketplace

import com.ongo.application.creatormarketplace.CreatorMarketplaceUseCase
import com.ongo.application.creatormarketplace.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "크리에이터 마켓플레이스", description = "크리에이터 서비스 마켓")
@RestController
@RequestMapping("/api/v1/creator-marketplace")
class CreatorMarketplaceController(
    private val creatorMarketplaceUseCase: CreatorMarketplaceUseCase
) {

    @Operation(summary = "리스팅 목록 조회")
    @GetMapping
    fun getListings(
        @RequestParam(required = false) serviceType: String?,
    ): ResponseEntity<ResData<List<MarketplaceListingResponse>>> {
        val result = creatorMarketplaceUseCase.getListings(serviceType)
        return ResData.success(result)
    }

    @Operation(summary = "리스팅 등록")
    @PostMapping
    fun createListing(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateListingRequest,
    ): ResponseEntity<ResData<MarketplaceListingResponse>> {
        val result = creatorMarketplaceUseCase.createListing(userId, request)
        return ResData.success(result, "서비스가 등록되었습니다")
    }

    @Operation(summary = "내 주문 목록 조회")
    @GetMapping("/orders")
    fun getOrders(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<MarketplaceOrderResponse>>> {
        val result = creatorMarketplaceUseCase.getOrders(userId)
        return ResData.success(result)
    }

    @Operation(summary = "주문 생성")
    @PostMapping("/{listingId}/order")
    fun placeOrder(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable listingId: Long,
    ): ResponseEntity<ResData<MarketplaceOrderResponse>> {
        val result = creatorMarketplaceUseCase.placeOrder(userId, listingId, "")
        return ResData.success(result, "주문이 접수되었습니다")
    }

    @Operation(summary = "마켓플레이스 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<MarketplaceSummaryResponse>> {
        val result = creatorMarketplaceUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
