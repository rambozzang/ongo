package com.ongo.api.repurpose

import com.ongo.application.repurpose.RepurposeUseCase
import com.ongo.application.repurpose.dto.RepurposeDetailResponse
import com.ongo.application.repurpose.dto.RepurposeJobResponse
import com.ongo.common.ResData
import com.ongo.common.annotation.RequiresPermission
import com.ongo.common.enums.Permission
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "콘텐츠 리퍼포징", description = "AI 기반 숏폼 클립 후보 추출 (10크레딧 소모)")
@RestController
@RequestMapping("/api/v1")
class RepurposeController(
    private val repurposeUseCase: RepurposeUseCase,
) {

    @Operation(
        summary = "영상 리퍼포징 분석 (10크레딧)",
        description = "영상의 설명/트랜스크립트를 AI가 분석하여 숏폼 클립 후보 구간(타임스탬프)과 추천 제목/설명을 제안합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "리퍼포징 분석 성공"),
        ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음"),
        ApiResponse(responseCode = "403", description = "접근 권한 없음"),
        ApiResponse(responseCode = "402", description = "크레딧 부족"),
    )
    @RequiresPermission(Permission.AI_USE)
    @PostMapping("/videos/{videoId}/repurpose")
    fun analyzeForRepurpose(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable videoId: Long,
    ): ResponseEntity<ResData<RepurposeDetailResponse>> {
        val result = repurposeUseCase.analyzeForRepurpose(userId, videoId)
        return ResData.success(result, "리퍼포징 분석이 완료되었습니다")
    }

    @Operation(
        summary = "내 리퍼포징 작업 목록 조회",
        description = "사용자가 요청한 리퍼포징 작업 목록을 반환합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
    )
    @GetMapping("/repurpose")
    fun getRepurposeJobs(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ResData<List<RepurposeJobResponse>>> {
        val result = repurposeUseCase.getRepurposeJobs(userId)
        return ResData.success(result)
    }

    @Operation(
        summary = "리퍼포징 작업 상세 조회",
        description = "특정 리퍼포징 작업의 상세 정보와 클립 후보 목록을 반환합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "404", description = "작업을 찾을 수 없음"),
        ApiResponse(responseCode = "403", description = "접근 권한 없음"),
    )
    @GetMapping("/repurpose/{jobId}")
    fun getRepurposeDetail(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable jobId: Long,
    ): ResponseEntity<ResData<RepurposeDetailResponse>> {
        val result = repurposeUseCase.getRepurposeDetail(userId, jobId)
        return ResData.success(result)
    }
}
