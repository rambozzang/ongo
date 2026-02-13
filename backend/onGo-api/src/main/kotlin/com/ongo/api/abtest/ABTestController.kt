package com.ongo.api.abtest

import com.ongo.application.abtest.ABTestStatisticsService
import com.ongo.application.abtest.ABTestUseCase
import com.ongo.application.abtest.dto.ABTestListResponse
import com.ongo.application.abtest.dto.ABTestResponse
import com.ongo.application.abtest.dto.ABTestStatisticsResponse
import com.ongo.application.abtest.dto.CreateABTestRequest
import com.ongo.application.abtest.dto.UpdateABTestRequest
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "A/B 테스트", description = "A/B 테스트 생성, 관리, 시작/중지")
@RestController
@RequestMapping("/api/v1/ab-tests")
class ABTestController(
    private val abTestUseCase: ABTestUseCase,
    private val abTestStatisticsService: ABTestStatisticsService,
) {

    @Operation(summary = "A/B 테스트 목록 조회")
    @GetMapping
    fun listTests(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<ABTestListResponse>> {
        return ResData.success(abTestUseCase.listTests(userId))
    }

    @Operation(summary = "A/B 테스트 상세 조회")
    @GetMapping("/{id}")
    fun getTest(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<ABTestResponse>> {
        return ResData.success(abTestUseCase.getTest(userId, id))
    }

    @Operation(summary = "A/B 테스트 생성")
    @PostMapping
    fun createTest(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: CreateABTestRequest,
    ): ResponseEntity<ResData<ABTestResponse>> {
        val result = abTestUseCase.createTest(userId, request)
        return ResData.success(result, "A/B 테스트가 생성되었습니다")
    }

    @Operation(summary = "A/B 테스트 수정")
    @PutMapping("/{id}")
    fun updateTest(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateABTestRequest,
    ): ResponseEntity<ResData<ABTestResponse>> {
        val result = abTestUseCase.updateTest(userId, id, request)
        return ResData.success(result, "A/B 테스트가 수정되었습니다")
    }

    @Operation(summary = "A/B 테스트 삭제")
    @DeleteMapping("/{id}")
    fun deleteTest(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        abTestUseCase.deleteTest(userId, id)
        return ResData.success(null, "A/B 테스트가 삭제되었습니다")
    }

    @Operation(summary = "A/B 테스트 통계 조회", description = "카이제곱 검정, 신뢰 구간, 표본 크기 진행률, 자동 승자 판정 결과를 포함한 통계를 조회합니다.")
    @GetMapping("/{id}/statistics")
    fun getStatistics(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<ABTestStatisticsResponse>> {
        return ResData.success(abTestStatisticsService.getStatistics(userId, id))
    }

    @Operation(summary = "A/B 테스트 시작")
    @PostMapping("/{id}/start")
    fun startTest(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<ABTestResponse>> {
        val result = abTestUseCase.startTest(userId, id)
        return ResData.success(result, "A/B 테스트가 시작되었습니다")
    }

    @Operation(summary = "A/B 테스트 중지")
    @PostMapping("/{id}/stop")
    fun stopTest(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<ABTestResponse>> {
        val result = abTestUseCase.stopTest(userId, id)
        return ResData.success(result, "A/B 테스트가 종료되었습니다")
    }
}
