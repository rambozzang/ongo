package com.ongo.api.videoscriptassistant

import com.ongo.application.videoscriptassistant.VideoScriptAssistantUseCase
import com.ongo.application.videoscriptassistant.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "AI 비디오 스크립트 어시스턴트", description = "AI 기반 비디오 스크립트 작성 및 개선")
@RestController
@RequestMapping("/api/v1/video-script-assistant")
class VideoScriptAssistantController(
    private val videoScriptAssistantUseCase: VideoScriptAssistantUseCase
) {

    @Operation(summary = "스크립트 목록 조회")
    @GetMapping
    fun getScripts(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<VideoScriptResponse>>> {
        val result = videoScriptAssistantUseCase.getScripts(userId)
        return ResData.success(result)
    }

    @Operation(summary = "스크립트 생성")
    @PostMapping("/generate")
    fun generate(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: GenerateScriptRequest,
    ): ResponseEntity<ResData<VideoScriptResponse>> {
        val result = videoScriptAssistantUseCase.generate(userId, request)
        return ResData.success(result, "스크립트가 생성되었습니다")
    }

    @Operation(summary = "스크립트 제안 조회")
    @GetMapping("/{scriptId}/suggestions")
    fun getSuggestions(
        @PathVariable scriptId: Long,
    ): ResponseEntity<ResData<List<ScriptSuggestionResponse>>> {
        val result = videoScriptAssistantUseCase.getSuggestions(scriptId)
        return ResData.success(result)
    }

    @Operation(summary = "제안 적용")
    @PostMapping("/{scriptId}/suggestions/{suggestionId}/apply")
    fun applySuggestion(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable scriptId: Long,
        @PathVariable suggestionId: Long,
    ): ResponseEntity<ResData<VideoScriptResponse>> {
        val result = videoScriptAssistantUseCase.applySuggestion(userId, scriptId, suggestionId)
        return ResData.success(result)
    }

    @Operation(summary = "스크립트 어시스턴트 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<VideoScriptAssistantSummaryResponse>> {
        val result = videoScriptAssistantUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
