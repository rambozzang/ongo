package com.ongo.api.contentabanalyzer

import com.ongo.application.contentabanalyzer.ContentAbAnalyzerUseCase
import com.ongo.application.contentabanalyzer.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "콘텐츠 A/B 분석기", description = "콘텐츠 스타일 비교 분석")
@RestController
@RequestMapping("/api/v1/content-ab-analyzer")
class ContentAbAnalyzerController(
    private val contentAbAnalyzerUseCase: ContentAbAnalyzerUseCase
) {

    @Operation(summary = "A/B 테스트 목록 조회")
    @GetMapping
    fun getTests(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<ContentAbTestResponse>>> {
        val result = contentAbAnalyzerUseCase.getTests(userId, status)
        return ResData.success(result)
    }

    @Operation(summary = "A/B 테스트 생성")
    @PostMapping
    fun create(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateAbTestRequest,
    ): ResponseEntity<ResData<ContentAbTestResponse>> {
        val result = contentAbAnalyzerUseCase.createTest(userId, request)
        return ResData.success(result, "A/B 테스트가 생성되었습니다")
    }

    @Operation(summary = "A/B 테스트 완료 처리")
    @PutMapping("/{id}/complete")
    fun complete(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<ContentAbTestResponse>> {
        val result = contentAbAnalyzerUseCase.completeTest(userId, id)
        return ResData.success(result, "A/B 테스트가 완료되었습니다")
    }

    @Operation(summary = "A/B 테스트 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<ContentAbSummaryResponse>> {
        val result = contentAbAnalyzerUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
