package com.ongo.api.contentrepurposer

import com.ongo.application.contentrepurposer.ContentRepurposerUseCase
import com.ongo.application.contentrepurposer.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "콘텐츠 리퍼포징", description = "긴 콘텐츠를 숏폼으로 자동 변환")
@RestController
@RequestMapping("/api/v1/content-repurposer")
class ContentRepurposerController(
    private val useCase: ContentRepurposerUseCase
) {

    @Operation(summary = "리퍼포징 작업 목록 조회")
    @GetMapping("/jobs")
    fun getJobs(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<RepurposeJobResponse>>> {
        val result = useCase.getJobs(userId, status)
        return ResData.success(result)
    }

    @Operation(summary = "리퍼포징 작업 생성")
    @PostMapping("/jobs")
    fun createJob(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: RepurposeRequest,
    ): ResponseEntity<ResData<RepurposeJobResponse>> {
        val result = useCase.createJob(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "리퍼포징 템플릿 목록 조회")
    @GetMapping("/templates")
    fun getTemplates(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<RepurposeTemplateResponse>>> {
        val result = useCase.getTemplates(userId)
        return ResData.success(result)
    }

    @Operation(summary = "리퍼포징 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<ContentRepurposerSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
