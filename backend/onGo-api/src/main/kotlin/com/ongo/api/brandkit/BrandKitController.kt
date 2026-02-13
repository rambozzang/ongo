package com.ongo.api.brandkit

import com.ongo.application.brandkit.BrandKitUseCase
import com.ongo.application.brandkit.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "브랜드 키트", description = "브랜드 아이덴티티 관리")
@RestController
@RequestMapping("/api/v1/brand-kit")
class BrandKitController(
    private val brandKitUseCase: BrandKitUseCase
) {

    @Operation(summary = "브랜드 키트 목록 조회")
    @GetMapping
    fun listBrandKits(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<BrandKitResponse>>> {
        val result = brandKitUseCase.listBrandKits(userId)
        return ResData.success(result)
    }

    @Operation(summary = "브랜드 키트 생성")
    @PostMapping
    fun createBrandKit(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateBrandKitRequest,
    ): ResponseEntity<ResData<BrandKitResponse>> {
        val result = brandKitUseCase.createBrandKit(userId, request)
        return ResData.success(result, "브랜드 키트가 생성되었습니다")
    }

    @Operation(summary = "브랜드 키트 수정")
    @PutMapping("/{id}")
    fun updateBrandKit(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateBrandKitRequest,
    ): ResponseEntity<ResData<BrandKitResponse>> {
        val result = brandKitUseCase.updateBrandKit(userId, id, request)
        return ResData.success(result)
    }

    @Operation(summary = "브랜드 키트 삭제")
    @DeleteMapping("/{id}")
    fun deleteBrandKit(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        brandKitUseCase.deleteBrandKit(userId, id)
        return ResData.success(null, "브랜드 키트가 삭제되었습니다")
    }

    @Operation(summary = "기본 브랜드 키트 설정")
    @PutMapping("/{id}/default")
    fun setDefault(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<BrandKitResponse>> {
        val result = brandKitUseCase.setDefault(userId, id)
        return ResData.success(result, "기본 브랜드 키트로 설정되었습니다")
    }
}
