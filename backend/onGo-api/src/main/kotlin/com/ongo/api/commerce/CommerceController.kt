package com.ongo.api.commerce

import com.ongo.application.commerce.CommerceUseCase
import com.ongo.application.commerce.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "소셜 커머스", description = "커머스 플랫폼 연동 및 상품 관리")
@RestController
@RequestMapping("/api/v1/commerce")
class CommerceController(
    private val commerceUseCase: CommerceUseCase
) {

    @Operation(summary = "연동 플랫폼 목록 조회")
    @GetMapping("/platforms")
    fun listPlatforms(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<CommercePlatformResponse>>> {
        val result = commerceUseCase.listPlatforms(userId)
        return ResData.success(result)
    }

    @Operation(summary = "커머스 플랫폼 연동")
    @PostMapping("/platforms")
    fun connectPlatform(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: ConnectPlatformRequest,
    ): ResponseEntity<ResData<CommercePlatformResponse>> {
        val result = commerceUseCase.connectPlatform(userId, request)
        return ResData.success(result, "플랫폼이 연동되었습니다")
    }

    @Operation(summary = "커머스 플랫폼 연동 해제")
    @DeleteMapping("/platforms/{id}")
    fun disconnectPlatform(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        commerceUseCase.disconnectPlatform(userId, id)
        return ResData.success(null, "플랫폼 연동이 해제되었습니다")
    }

    @Operation(summary = "상품 목록 조회")
    @GetMapping("/products")
    fun listProducts(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<CommerceProductResponse>>> {
        val result = commerceUseCase.listProducts(userId)
        return ResData.success(result)
    }

    @Operation(summary = "상품 등록")
    @PostMapping("/products")
    fun createProduct(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateProductRequest,
    ): ResponseEntity<ResData<CommerceProductResponse>> {
        val result = commerceUseCase.createProduct(userId, request)
        return ResData.success(result, "상품이 등록되었습니다")
    }

    @Operation(summary = "상품 수정")
    @PutMapping("/products/{id}")
    fun updateProduct(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateProductRequest,
    ): ResponseEntity<ResData<CommerceProductResponse>> {
        val result = commerceUseCase.updateProduct(userId, id, request)
        return ResData.success(result)
    }

    @Operation(summary = "상품 삭제")
    @DeleteMapping("/products/{id}")
    fun deleteProduct(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        commerceUseCase.deleteProduct(userId, id)
        return ResData.success(null, "상품이 삭제되었습니다")
    }

    @Operation(summary = "상품-영상 연결")
    @PostMapping("/product-links")
    fun linkProductToVideo(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: LinkProductVideoRequest,
    ): ResponseEntity<ResData<ProductVideoLinkResponse>> {
        val result = commerceUseCase.linkProductToVideo(userId, request)
        return ResData.success(result, "상품이 영상에 연결되었습니다")
    }

    @Operation(summary = "영상에 연결된 상품 조회")
    @GetMapping("/product-links")
    fun getVideoLinks(
        @RequestParam videoId: Long,
    ): ResponseEntity<ResData<List<ProductVideoLinkResponse>>> {
        val result = commerceUseCase.getVideoLinks(videoId)
        return ResData.success(result)
    }
}
