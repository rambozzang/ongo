package com.ongo.api.ugc

import com.ongo.application.ugc.shorts.ShortsPromptUseCase
import com.ongo.application.ugc.shorts.dto.ShortsPromptResponse
import com.ongo.application.ugc.shorts.dto.ShortsPromptRevisionResponse
import com.ongo.application.ugc.shorts.dto.UpdateShortsPromptRequest
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
import org.springframework.web.bind.annotation.RestController

@Tag(name = "UGC 쇼츠 프롬프트", description = "쇼츠 파이프라인 단계별 프롬프트 조회·편집·개정 이력 관리")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/ugc/shorts/prompts")
class ShortsPromptController(
    private val shortsPromptUseCase: ShortsPromptUseCase,
) {

    @Operation(summary = "프롬프트 목록 조회", description = "9단계 전체를 파이프라인 순서대로 반환합니다.")
    @GetMapping
    @RequiresPermission(Permission.SHORTS_PIPELINE_VIEW)
    fun list(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
    ): ResponseEntity<ResData<List<ShortsPromptResponse>>> =
        ResData.success(shortsPromptUseCase.listPrompts(userId, workspaceId))

    @Operation(summary = "단계별 프롬프트 조회")
    @GetMapping("/{stage}")
    @RequiresPermission(Permission.SHORTS_PIPELINE_VIEW)
    fun get(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable stage: String,
    ): ResponseEntity<ResData<ShortsPromptResponse>> =
        ResData.success(shortsPromptUseCase.getPrompt(userId, workspaceId, stage))

    @Operation(summary = "프롬프트 편집", description = "워크스페이스 오버라이드를 생성/갱신하고 revision을 1 올립니다.")
    @PutMapping("/{stage}")
    @RequiresPermission(Permission.SHORTS_PIPELINE_MANAGE)
    fun update(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable stage: String,
        @RequestBody request: UpdateShortsPromptRequest,
    ): ResponseEntity<ResData<ShortsPromptResponse>> =
        ResData.success(shortsPromptUseCase.updatePrompt(userId, workspaceId, stage, request), "프롬프트가 저장되었습니다")

    @Operation(summary = "기본값으로 복원", description = "워크스페이스 오버라이드를 삭제해 시스템 기본값으로 되돌립니다.")
    @DeleteMapping("/{stage}")
    @RequiresPermission(Permission.SHORTS_PIPELINE_MANAGE)
    fun reset(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable stage: String,
    ): ResponseEntity<ResData<ShortsPromptResponse>> =
        ResData.success(shortsPromptUseCase.resetPrompt(userId, workspaceId, stage), "기본값으로 복원되었습니다")

    @Operation(summary = "개정 이력 조회", description = "최신 개정부터 반환합니다.")
    @GetMapping("/{stage}/revisions")
    @RequiresPermission(Permission.SHORTS_PIPELINE_VIEW)
    fun revisions(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable stage: String,
    ): ResponseEntity<ResData<List<ShortsPromptRevisionResponse>>> =
        ResData.success(shortsPromptUseCase.listRevisions(userId, workspaceId, stage))

    @Operation(summary = "개정 롤백", description = "지정 개정의 내용으로 새 개정을 만듭니다.")
    @PostMapping("/{stage}/revisions/{revision}/restore")
    @RequiresPermission(Permission.SHORTS_PIPELINE_MANAGE)
    fun restore(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable stage: String,
        @PathVariable revision: Int,
    ): ResponseEntity<ResData<ShortsPromptResponse>> =
        ResData.success(shortsPromptUseCase.restoreRevision(userId, workspaceId, stage, revision), "개정 $revision 으로 롤백되었습니다")
}
