package com.ongo.api.ugc

import com.ongo.application.ugc.shorts.ShortsTemplateUseCase
import com.ongo.application.ugc.shorts.dto.ShortsTemplateRequest
import com.ongo.application.ugc.shorts.dto.ShortsTemplateResponse
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import com.ongo.common.annotation.RequiresPermission
import com.ongo.common.enums.Permission
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@Tag(name = "UGC 쇼츠 템플릿", description = "쇼츠 템플릿(폰트·색·배경·세이프에어리어) 관리")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/ugc/shorts/templates")
class ShortsTemplateController(
    private val shortsTemplateUseCase: ShortsTemplateUseCase,
) {

    @Operation(summary = "템플릿 목록 조회")
    @GetMapping
    @RequiresPermission(Permission.SHORTS_PIPELINE_VIEW)
    fun list(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
    ): ResponseEntity<ResData<List<ShortsTemplateResponse>>> =
        ResData.success(shortsTemplateUseCase.listTemplates(userId, workspaceId))

    @Operation(summary = "템플릿 생성")
    @PostMapping
    @RequiresPermission(Permission.SHORTS_PIPELINE_MANAGE)
    fun create(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @RequestBody request: ShortsTemplateRequest,
    ): ResponseEntity<ResData<ShortsTemplateResponse>> =
        ResData.success(shortsTemplateUseCase.createTemplate(userId, workspaceId, request), "템플릿이 생성되었습니다")

    @Operation(summary = "템플릿 상세 조회")
    @GetMapping("/{templateId}")
    @RequiresPermission(Permission.SHORTS_PIPELINE_VIEW)
    fun get(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable templateId: Long,
    ): ResponseEntity<ResData<ShortsTemplateResponse>> =
        ResData.success(shortsTemplateUseCase.getTemplate(userId, workspaceId, templateId))

    @Operation(summary = "템플릿 수정")
    @PutMapping("/{templateId}")
    @RequiresPermission(Permission.SHORTS_PIPELINE_MANAGE)
    fun update(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable templateId: Long,
        @RequestBody request: ShortsTemplateRequest,
    ): ResponseEntity<ResData<ShortsTemplateResponse>> =
        ResData.success(shortsTemplateUseCase.updateTemplate(userId, workspaceId, templateId, request), "템플릿이 수정되었습니다")

    @Operation(summary = "템플릿 삭제")
    @DeleteMapping("/{templateId}")
    @RequiresPermission(Permission.SHORTS_PIPELINE_MANAGE)
    fun delete(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable templateId: Long,
    ): ResponseEntity<ResData<Unit>> {
        shortsTemplateUseCase.deleteTemplate(userId, workspaceId, templateId)
        return ResData.success(Unit, "템플릿이 삭제되었습니다")
    }

    @Operation(summary = "레퍼런스 이미지 업로드", description = "템플릿 레퍼런스 캡처를 업로드하고 URL을 반영합니다.")
    @PostMapping("/{templateId}/reference-image")
    @RequiresPermission(Permission.SHORTS_PIPELINE_MANAGE)
    fun uploadReferenceImage(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable templateId: Long,
        @RequestParam file: MultipartFile,
    ): ResponseEntity<ResData<ShortsTemplateResponse>> =
        ResData.success(
            shortsTemplateUseCase.uploadReferenceImage(userId, workspaceId, templateId, file),
            "레퍼런스 이미지가 업로드되었습니다",
        )
}
