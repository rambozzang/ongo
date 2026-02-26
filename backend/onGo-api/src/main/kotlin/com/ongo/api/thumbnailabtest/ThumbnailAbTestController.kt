package com.ongo.api.thumbnailabtest

import com.ongo.application.thumbnailabtest.ThumbnailAbTestUseCase
import com.ongo.application.thumbnailabtest.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "썸네일 A/B 테스트", description = "썸네일 변형 A/B 테스트 관리")
@RestController
@RequestMapping("/api/v1/thumbnail-ab-tests")
class ThumbnailAbTestController(
    private val useCase: ThumbnailAbTestUseCase
) {

    @Operation(summary = "테스트 목록 조회")
    @GetMapping
    fun getTests(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<ThumbnailTestResponse>>> {
        val result = useCase.getTests(userId, status)
        return ResData.success(result)
    }

    @Operation(summary = "테스트 생성")
    @PostMapping
    fun createTest(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateThumbnailTestRequest,
    ): ResponseEntity<ResData<ThumbnailTestResponse>> {
        val result = useCase.createTest(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "테스트 종료")
    @PostMapping("/{id}/end")
    fun endTest(
        @PathVariable id: Long,
    ): ResponseEntity<ResData<ThumbnailTestResponse?>> {
        val result = useCase.endTest(id)
        return ResData.success(result)
    }

    @Operation(summary = "테스트 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<ThumbnailAbTestSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
