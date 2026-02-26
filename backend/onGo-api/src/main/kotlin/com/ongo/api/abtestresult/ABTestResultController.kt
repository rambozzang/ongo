package com.ongo.api.abtestresult

import com.ongo.application.abtestresult.ABTestResultUseCase
import com.ongo.application.abtestresult.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "A/B 테스트 결과", description = "A/B 테스트 결과 분석 및 관리")
@RestController
@RequestMapping("/api/v1/ab-test-results")
class ABTestResultController(
    private val useCase: ABTestResultUseCase
) {

    @Operation(summary = "A/B 테스트 결과 목록 조회")
    @GetMapping
    fun getResults(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<ABTestResultResponse>>> {
        val result = useCase.getResults(userId, status)
        return ResData.success(result)
    }

    @Operation(summary = "A/B 테스트 결과 상세 조회")
    @GetMapping("/{id}")
    fun getResult(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<ABTestResultResponse>> {
        val result = useCase.getResult(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "A/B 테스트 일시중지")
    @PostMapping("/{id}/pause")
    fun pauseTest(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<ABTestResultResponse>> {
        val result = useCase.pauseTest(userId, id)
        return ResData.success(result, "테스트가 일시중지되었습니다")
    }

    @Operation(summary = "A/B 테스트 재개")
    @PostMapping("/{id}/resume")
    fun resumeTest(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<ABTestResultResponse>> {
        val result = useCase.resumeTest(userId, id)
        return ResData.success(result, "테스트가 재개되었습니다")
    }

    @Operation(summary = "A/B 테스트 중지 및 완료")
    @PostMapping("/{id}/stop")
    fun stopTest(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<ABTestResultResponse>> {
        val result = useCase.stopTest(userId, id)
        return ResData.success(result, "테스트가 완료되었습니다")
    }

    @Operation(summary = "A/B 테스트 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<ABTestResultSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
