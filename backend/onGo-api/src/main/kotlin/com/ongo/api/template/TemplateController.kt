package com.ongo.api.template

import com.ongo.api.config.CurrentUser
import com.ongo.application.template.TemplateUseCase
import com.ongo.application.template.dto.CreateTemplateRequest
import com.ongo.application.template.dto.TemplateListResponse
import com.ongo.application.template.dto.TemplateResponse
import com.ongo.application.template.dto.UpdateTemplateRequest
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "템플릿 관리", description = "메타데이터 템플릿 CRUD 관리")
@RestController
@RequestMapping("/api/v1/templates")
class TemplateController(
    private val templateUseCase: TemplateUseCase,
) {

    @Operation(summary = "템플릿 목록 조회", description = "사용자의 템플릿 목록을 조회합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "템플릿 목록 조회 성공"),
    )
    @GetMapping
    fun listTemplates(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<TemplateListResponse>> {
        val result = templateUseCase.listTemplates(userId, page, size)
        return ResData.success(result)
    }

    @Operation(summary = "템플릿 생성", description = "새로운 메타데이터 템플릿을 생성합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "템플릿 생성 성공"),
    )
    @PostMapping
    fun createTemplate(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: CreateTemplateRequest,
    ): ResponseEntity<ResData<TemplateResponse>> {
        val result = templateUseCase.createTemplate(userId, request)
        return ResData.success(result, "템플릿이 생성되었습니다")
    }

    @Operation(summary = "템플릿 수정", description = "기존 템플릿을 수정합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "템플릿 수정 성공"),
        ApiResponse(responseCode = "404", description = "템플릿을 찾을 수 없음"),
    )
    @PutMapping("/{id}")
    fun updateTemplate(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateTemplateRequest,
    ): ResponseEntity<ResData<TemplateResponse>> {
        val result = templateUseCase.updateTemplate(userId, id, request)
        return ResData.success(result, "템플릿이 수정되었습니다")
    }

    @Operation(summary = "템플릿 삭제", description = "기존 템플릿을 삭제합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "템플릿 삭제 성공"),
        ApiResponse(responseCode = "404", description = "템플릿을 찾을 수 없음"),
    )
    @DeleteMapping("/{id}")
    fun deleteTemplate(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        templateUseCase.deleteTemplate(userId, id)
        return ResData.success(null, "템플릿이 삭제되었습니다")
    }

    @Operation(summary = "템플릿 사용", description = "템플릿 사용 횟수를 증가시킵니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "템플릿 사용 기록 성공"),
        ApiResponse(responseCode = "404", description = "템플릿을 찾을 수 없음"),
    )
    @PostMapping("/{id}/use")
    fun useTemplate(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<TemplateResponse>> {
        val result = templateUseCase.useTemplate(userId, id)
        return ResData.success(result)
    }
}
