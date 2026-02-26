package com.ongo.api.contentversioning

import com.ongo.application.contentversioning.ContentVersioningUseCase
import com.ongo.application.contentversioning.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "콘텐츠 버전 관리", description = "콘텐츠 수정 이력 추적 및 성과 비교")
@RestController
@RequestMapping("/api/v1/content-versioning")
class ContentVersioningController(
    private val useCase: ContentVersioningUseCase
) {

    @Operation(summary = "버전 그룹 목록 조회")
    @GetMapping
    fun getGroups(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<ContentVersionGroupResponse>>> {
        val result = useCase.getGroups(userId)
        return ResData.success(result)
    }

    @Operation(summary = "콘텐츠별 버전 이력 조회")
    @GetMapping("/{contentId}/versions")
    fun getVersions(
        @PathVariable contentId: Long,
    ): ResponseEntity<ResData<List<ContentVersionResponse>>> {
        val result = useCase.getVersions(contentId)
        return ResData.success(result)
    }

    @Operation(summary = "콘텐츠 버전 관리 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<ContentVersioningSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
