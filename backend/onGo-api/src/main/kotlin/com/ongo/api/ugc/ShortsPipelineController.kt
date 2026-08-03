package com.ongo.api.ugc

import com.ongo.application.ugc.shorts.ShortsPipelineUseCase
import com.ongo.application.ugc.shorts.dto.AttachRenderedVideoRequest
import com.ongo.application.ugc.shorts.dto.CreatePipelineRunRequest
import com.ongo.application.ugc.shorts.dto.HookSelectionRequest
import com.ongo.application.ugc.shorts.dto.PipelineRunDetailResponse
import com.ongo.application.ugc.shorts.dto.PipelineRunResponse
import com.ongo.application.ugc.shorts.dto.ScheduleConfirmRequest
import com.ongo.application.ugc.shorts.dto.ShortsClipResponse
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import com.ongo.common.config.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "UGC 쇼츠 파이프라인", description = "롱폼에서 쇼츠를 뽑아내는 파이프라인 실행·후킹 선택·예약")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/ugc/shorts/runs")
class ShortsPipelineController(
    private val pipelineUseCase: ShortsPipelineUseCase,
) {

    @Operation(summary = "파이프라인 실행 생성 및 시작")
    @PostMapping
    fun create(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @RequestBody request: CreatePipelineRunRequest,
    ): ResponseEntity<ResData<PipelineRunResponse>> =
        ResData.success(pipelineUseCase.createRun(userId, workspaceId, request))

    @Operation(summary = "파이프라인 실행 목록 조회")
    @GetMapping
    fun list(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<PageResponse<PipelineRunResponse>>> {
        val result = pipelineUseCase.listRuns(userId, workspaceId, page, size)
        return ResData.success(
            PageResponse.of(
                content = result.runs,
                page = result.page,
                size = result.size,
                totalElements = result.total,
            )
        )
    }

    @Operation(summary = "파이프라인 실행 상세 조회", description = "단계별 진행 상태와 클립·후킹을 함께 반환한다")
    @GetMapping("/{runId}")
    fun get(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
    ): ResponseEntity<ResData<PipelineRunDetailResponse>> =
        ResData.success(pipelineUseCase.getRunDetail(userId, workspaceId, runId))

    @Operation(
        summary = "특정 단계부터 재실행",
        description = "해당 단계와 그 이후 단계의 결과를 무효화하고 다시 실행한다. 프롬프트를 고친 뒤 사용한다",
    )
    @PostMapping("/{runId}/stages/{stage}/rerun")
    fun rerunStage(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
        @PathVariable stage: String,
    ): ResponseEntity<ResData<PipelineRunResponse>> =
        ResData.success(pipelineUseCase.rerunStage(userId, workspaceId, runId, stage))

    @Operation(summary = "후킹 문구 일괄 선택", description = "클립별 A/B안 선택 또는 직접 입력. 제외할 클립은 discardClipIds 로 넘긴다")
    @PostMapping("/{runId}/hooks")
    fun selectHooks(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
        @RequestBody request: HookSelectionRequest,
    ): ResponseEntity<ResData<PipelineRunResponse>> =
        ResData.success(pipelineUseCase.selectHooks(userId, workspaceId, runId, request).run)

    @Operation(summary = "예약 확정", description = "시작 일시부터 지정 간격으로 클립을 순차 예약한다")
    @PostMapping("/{runId}/schedule")
    fun confirmSchedule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
        @RequestBody request: ScheduleConfirmRequest,
    ): ResponseEntity<ResData<PipelineRunResponse>> =
        ResData.success(pipelineUseCase.confirmSchedule(userId, workspaceId, runId, request))

    @Operation(
        summary = "렌더 완성 영상 연결",
        description = "render.sh 로 만든 완성 영상을 업로드한 뒤 그 videoId 를 연결한다. 연결해야 게시 대상이 된다",
    )
    @PostMapping("/{runId}/clips/{clipId}/rendered-video")
    fun attachRenderedVideo(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
        @PathVariable clipId: Long,
        @RequestBody request: AttachRenderedVideoRequest,
    ): ResponseEntity<ResData<ShortsClipResponse>> =
        ResData.success(
            pipelineUseCase.attachRenderedVideo(userId, workspaceId, runId, clipId, request.videoId),
        )

    @Operation(summary = "클립 렌더 지시서 다운로드", description = "render-spec.json 단일 파일")
    @GetMapping("/{runId}/clips/{clipId}/render-spec")
    fun downloadRenderSpec(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
        @PathVariable clipId: Long,
    ): ResponseEntity<ByteArray> {
        val json = pipelineUseCase.getRenderSpec(userId, workspaceId, runId, clipId)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"render-spec-$clipId.json\"")
            .contentType(MediaType.parseMediaType("application/json; charset=UTF-8"))
            .body(json.toByteArray(Charsets.UTF_8))
    }

    @Operation(
        summary = "렌더 산출물 묶음 다운로드",
        description = "render-spec.json, clip-N.ass 자막, render.sh 를 zip 으로 내려준다. 실제 인코딩은 하지 않는다",
    )
    @GetMapping("/{runId}/render-bundle")
    fun downloadRenderBundle(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
    ): ResponseEntity<ByteArray> {
        val zip = pipelineUseCase.getRenderBundle(userId, workspaceId, runId)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"shorts-run-$runId-render.zip\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(zip)
    }

    @Operation(summary = "파이프라인 실행 삭제")
    @DeleteMapping("/{runId}")
    fun delete(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
    ): ResponseEntity<ResData<Unit>> {
        pipelineUseCase.deleteRun(userId, workspaceId, runId)
        return ResData.success(Unit)
    }
}
