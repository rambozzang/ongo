package com.ongo.api.scriptwriter

import com.ongo.application.scriptwriter.ScriptWriterUseCase
import com.ongo.application.scriptwriter.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "AI 스크립트 작성기", description = "AI 기반 영상 대본 생성 및 관리")
@RestController
@RequestMapping("/api/v1/scripts")
class ScriptWriterController(
    private val scriptWriterUseCase: ScriptWriterUseCase
) {

    @Operation(summary = "스크립트 목록 조회")
    @GetMapping
    fun getScripts(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<ScriptResponse>>> {
        val result = scriptWriterUseCase.getScripts(userId, status)
        return ResData.success(result)
    }

    @Operation(summary = "스크립트 상세 조회")
    @GetMapping("/{id}")
    fun getScript(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<ScriptResponse>> {
        val result = scriptWriterUseCase.getScript(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "AI 스크립트 생성")
    @PostMapping("/generate")
    fun generateScript(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: GenerateScriptRequest,
    ): ResponseEntity<ResData<ScriptResponse>> {
        val result = scriptWriterUseCase.generateScript(userId, request)
        return ResData.success(result, "스크립트가 생성되었습니다")
    }

    @Operation(summary = "스크립트 수정")
    @PutMapping("/{id}")
    fun updateScript(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateScriptRequest,
    ): ResponseEntity<ResData<ScriptResponse>> {
        val result = scriptWriterUseCase.updateScript(userId, id, request)
        return ResData.success(result, "스크립트가 수정되었습니다")
    }

    @Operation(summary = "스크립트 삭제")
    @DeleteMapping("/{id}")
    fun deleteScript(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        scriptWriterUseCase.deleteScript(userId, id)
        return ResData.success(null, "스크립트가 삭제되었습니다")
    }

    @Operation(summary = "스크립트 요약 조회")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<ScriptSummaryResponse>> {
        val result = scriptWriterUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
