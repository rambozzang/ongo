package com.ongo.api.brandsafety

import com.ongo.application.brandsafety.BrandSafetyUseCase
import com.ongo.application.brandsafety.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "브랜드 안전성", description = "콘텐츠 브랜드 안전성 점검 및 규칙 관리")
@RestController
@RequestMapping("/api/v1/brand-safety")
class BrandSafetyController(
    private val useCase: BrandSafetyUseCase
) {

    @Operation(summary = "안전성 점검 목록 조회")
    @GetMapping("/checks")
    fun getChecks(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<BrandSafetyCheckResponse>>> {
        val result = useCase.getChecks(userId, status)
        return ResData.success(result)
    }

    @Operation(summary = "안전성 점검 상세 조회")
    @GetMapping("/checks/{id}")
    fun getCheck(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<BrandSafetyCheckResponse>> {
        val result = useCase.getCheck(id) ?: throw IllegalArgumentException("점검 결과를 찾을 수 없습니다")
        return ResData.success(result)
    }

    @Operation(summary = "안전성 점검 실행")
    @PostMapping("/checks")
    fun runCheck(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: RunSafetyCheckRequest,
    ): ResponseEntity<ResData<BrandSafetyCheckResponse>> {
        val result = useCase.runCheck(userId, request)
        return ResData.success(result, "안전성 점검이 시작되었습니다")
    }

    @Operation(summary = "안전성 규칙 목록 조회")
    @GetMapping("/rules")
    fun getRules(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<BrandSafetyRuleResponse>>> {
        val result = useCase.getRules(userId)
        return ResData.success(result)
    }

    @Operation(summary = "안전성 규칙 활성화/비활성화")
    @PatchMapping("/rules/{id}")
    fun toggleRule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: ToggleRuleRequest,
    ): ResponseEntity<ResData<BrandSafetyRuleResponse>> {
        val result = useCase.toggleRule(userId, id, request.isEnabled)
        return ResData.success(result, "규칙이 업데이트되었습니다")
    }

    @Operation(summary = "안전성 점검 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<BrandSafetySummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
