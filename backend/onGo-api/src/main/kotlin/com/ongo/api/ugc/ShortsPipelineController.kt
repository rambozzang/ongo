package com.ongo.api.ugc

import com.ongo.api.ugc.dto.ShortsRenderJobResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
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
import com.ongo.common.annotation.RequiresPermission
import com.ongo.common.config.PageResponse
import com.ongo.common.enums.Permission
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
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "UGC 쇼츠 파이프라인", description = "롱폼에서 쇼츠를 뽑아내는 파이프라인 실행·후킹 선택·예약")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/ugc/shorts/runs")
class ShortsPipelineController(
    private val renderUseCase: com.ongo.application.ugc.shorts.ShortsRenderUseCase,
    private val pipelineUseCase: ShortsPipelineUseCase,
) {

    @Operation(summary = "파이프라인 실행 생성 및 시작")
    @PostMapping
    @RequiresPermission(Permission.SHORTS_PIPELINE_MANAGE)
    fun create(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody request: CreatePipelineRunRequest,
    ): ResponseEntity<ResData<PipelineRunResponse>> =
        ResData.success(pipelineUseCase.createRun(userId, workspaceId, request, idempotencyKey))

    @Operation(summary = "파이프라인 실행 목록 조회")
    @GetMapping
    @RequiresPermission(Permission.SHORTS_PIPELINE_VIEW)
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
    @RequiresPermission(Permission.SHORTS_PIPELINE_VIEW)
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
    @RequiresPermission(Permission.SHORTS_PIPELINE_MANAGE)
    fun rerunStage(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
        @PathVariable stage: String,
    ): ResponseEntity<ResData<PipelineRunResponse>> =
        ResData.success(pipelineUseCase.rerunStage(userId, workspaceId, runId, stage))

    @Operation(summary = "후킹 문구 일괄 선택", description = "클립별 A/B안 선택 또는 직접 입력. 제외할 클립은 discardClipIds 로 넘긴다")
    @PostMapping("/{runId}/hooks")
    @RequiresPermission(Permission.SHORTS_PIPELINE_MANAGE)
    fun selectHooks(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
        @RequestBody request: HookSelectionRequest,
    ): ResponseEntity<ResData<PipelineRunResponse>> =
        ResData.success(pipelineUseCase.selectHooks(userId, workspaceId, runId, request).run)

    @Operation(summary = "예약 확정", description = "시작 일시부터 지정 간격으로 클립을 순차 예약한다")
    @PostMapping("/{runId}/schedule")
    @RequiresPermission(Permission.SHORTS_PIPELINE_MANAGE)
    fun confirmSchedule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
        @RequestBody request: ScheduleConfirmRequest,
    ): ResponseEntity<ResData<PipelineRunResponse>> =
        ResData.success(pipelineUseCase.confirmSchedule(userId, workspaceId, runId, request))

    @Operation(
        summary = "렌더 완성 영상 연결",
        description = "서버 렌더를 사용할 수 없을 때 외부에서 만든 완성 영상을 연결하는 수동 보완 경로",
    )
    @PostMapping("/{runId}/clips/{clipId}/rendered-video")
    @RequiresPermission(Permission.SHORTS_PIPELINE_MANAGE)
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
    @RequiresPermission(Permission.SHORTS_PIPELINE_VIEW)
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
        description = "서버 렌더를 직접 실행할 수 없는 환경에서 사용할 render-spec.json, clip-N.ass 자막, render.sh 묶음",
    )
    @GetMapping("/{runId}/render-bundle")
    @RequiresPermission(Permission.SHORTS_PIPELINE_VIEW)
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
    @RequiresPermission(Permission.SHORTS_PIPELINE_MANAGE)
    fun delete(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
    ): ResponseEntity<ResData<Unit>> {
        pipelineUseCase.deleteRun(userId, workspaceId, runId)
        return ResData.success(Unit)
    }

    @Operation(
        summary = "클립 서버 렌더 요청",
        description = "서버에서 ffmpeg 로 클립을 인코딩한다. 인코딩은 분 단위라 즉시 끝나지 않으므로 " +
            "작업만 접수하고 202 를 돌려준다. 진행 상황은 같은 경로의 GET 으로 묻는다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "202", description = "렌더 접수. 이미 진행 중이면 그 작업을 그대로 돌려준다"),
        ApiResponse(responseCode = "404", description = "실행 또는 클립 없음"),
    )
    @PostMapping("/{runId}/clips/{clipId}/render")
    @RequiresPermission(Permission.SHORTS_PIPELINE_MANAGE)
    fun requestRender(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
        @PathVariable clipId: Long,
    ): ResponseEntity<ResData<ShortsRenderJobResponse>> {
        val job = renderUseCase.requestRender(userId, workspaceId, runId, clipId)
        return ResponseEntity.accepted().body(ResData(data = ShortsRenderJobResponse.from(job)))
    }

    @Operation(summary = "클립 렌더 상태 조회", description = "QUEUED / RUNNING / COMPLETED / FAILED")
    @GetMapping("/{runId}/clips/{clipId}/render")
    @RequiresPermission(Permission.SHORTS_PIPELINE_VIEW)
    fun renderStatus(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable runId: Long,
        @PathVariable clipId: Long,
    ): ResponseEntity<ResData<ShortsRenderJobResponse>> =
        ResData.success(ShortsRenderJobResponse.from(renderUseCase.status(userId, workspaceId, runId, clipId)))
}
