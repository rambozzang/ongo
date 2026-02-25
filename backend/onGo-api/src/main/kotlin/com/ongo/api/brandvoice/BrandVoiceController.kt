package com.ongo.api.brandvoice

import com.ongo.application.brandvoice.BrandVoiceUseCase
import com.ongo.application.brandvoice.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "브랜드 보이스", description = "브랜드 보이스 프로필 생성 및 관리")
@RestController
@RequestMapping("/api/v1/brand-voice")
class BrandVoiceController(
    private val brandVoiceUseCase: BrandVoiceUseCase
) {

    @Operation(summary = "브랜드 보이스 프로필 목록 조회")
    @GetMapping("/profiles")
    fun listProfiles(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<BrandVoiceProfileResponse>>> {
        val result = brandVoiceUseCase.listProfiles(userId)
        return ResData.success(result)
    }

    @Operation(summary = "브랜드 보이스 프로필 상세 조회")
    @GetMapping("/profiles/{id}")
    fun getProfile(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<BrandVoiceProfileResponse>> {
        val result = brandVoiceUseCase.getProfile(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "브랜드 보이스 프로필 생성")
    @PostMapping("/profiles")
    fun createProfile(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateBrandVoiceProfileRequest,
    ): ResponseEntity<ResData<BrandVoiceProfileResponse>> {
        val result = brandVoiceUseCase.createProfile(userId, request)
        return ResData.success(result, "브랜드 보이스 프로필이 생성되었습니다")
    }

    @Operation(summary = "브랜드 보이스 프로필 삭제")
    @DeleteMapping("/profiles/{id}")
    fun deleteProfile(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        brandVoiceUseCase.deleteProfile(userId, id)
        return ResData.success(null, "브랜드 보이스 프로필이 삭제되었습니다")
    }
}
