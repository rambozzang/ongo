package com.ongo.api.thumbnailgenerator

import com.ongo.application.thumbnailgenerator.ThumbnailGeneratorUseCase
import com.ongo.application.thumbnailgenerator.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "AI 썸네일 생성기", description = "AI 기반 썸네일 자동 생성 및 관리")
@RestController
@RequestMapping("/api/v1/thumbnail-generator")
class ThumbnailGeneratorController(
    private val useCase: ThumbnailGeneratorUseCase
) {

    @Operation(summary = "썸네일 템플릿 목록 조회")
    @GetMapping("/templates")
    fun getTemplates(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<ThumbnailTemplatesWrapper>> {
        val result = useCase.getTemplates(userId)
        return ResData.success(result)
    }

    @Operation(summary = "AI 썸네일 생성")
    @PostMapping("/generate")
    fun generate(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: ThumbnailGenerateRequest,
    ): ResponseEntity<ResData<ThumbnailGenerateResponse>> {
        val result = useCase.generate(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "썸네일 생성 이력 조회")
    @GetMapping("/history")
    fun getHistory(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<ThumbnailHistoryWrapper>> {
        val result = useCase.getHistory(userId)
        return ResData.success(result)
    }

    @Operation(summary = "생성된 썸네일 선택")
    @PostMapping("/history/{historyId}/select/{thumbnailId}")
    fun selectThumbnail(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable historyId: Long,
        @PathVariable thumbnailId: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        useCase.selectThumbnail(userId, historyId, thumbnailId)
        return ResData.success(null, "썸네일이 선택되었습니다")
    }
}
