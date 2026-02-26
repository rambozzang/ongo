package com.ongo.api.mediakit

import com.ongo.application.mediakit.MediaKitUseCase
import com.ongo.application.mediakit.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "미디어 킷", description = "AI 미디어 킷 생성 및 관리")
@RestController
@RequestMapping("/api/v1/media-kit")
class MediaKitController(
    private val mediaKitUseCase: MediaKitUseCase
) {

    @Operation(summary = "미디어 킷 템플릿 조회")
    @GetMapping("/templates")
    fun getTemplates(): ResponseEntity<ResData<List<MediaKitTemplateResponse>>> {
        val result = mediaKitUseCase.getTemplates()
        return ResData.success(result)
    }

    @Operation(summary = "미디어 킷 생성")
    @PostMapping("/generate")
    fun generate(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: MediaKitGenerateRequest,
    ): ResponseEntity<ResData<MediaKitResponse>> {
        val result = mediaKitUseCase.generate(userId, request)
        return ResData.success(result, "미디어 킷이 생성되었습니다")
    }

    @Operation(summary = "내 미디어 킷 목록 조회")
    @GetMapping
    fun getMyKits(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<MediaKitResponse>>> {
        val result = mediaKitUseCase.getMyKits(userId)
        return ResData.success(result)
    }

    @Operation(summary = "미디어 킷 상세 조회")
    @GetMapping("/{id}")
    fun getKit(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<MediaKitResponse>> {
        val result = mediaKitUseCase.getKit(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "미디어 킷 수정")
    @PutMapping("/{id}")
    fun updateKit(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateMediaKitRequest,
    ): ResponseEntity<ResData<MediaKitResponse>> {
        val result = mediaKitUseCase.updateKit(userId, id, request)
        return ResData.success(result, "미디어 킷이 수정되었습니다")
    }

    @Operation(summary = "미디어 킷 발행")
    @PostMapping("/{id}/publish")
    fun publishKit(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<MediaKitResponse>> {
        val result = mediaKitUseCase.publishKit(userId, id)
        return ResData.success(result, "미디어 킷이 발행되었습니다")
    }

    @Operation(summary = "미디어 킷 삭제")
    @DeleteMapping("/{id}")
    fun deleteKit(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        mediaKitUseCase.deleteKit(userId, id)
        return ResData.success(null, "미디어 킷이 삭제되었습니다")
    }
}
