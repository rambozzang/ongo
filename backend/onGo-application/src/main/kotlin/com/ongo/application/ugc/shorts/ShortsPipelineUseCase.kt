package com.ongo.application.ugc.shorts

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ugc.shorts.dto.ClipHookResponse
import com.ongo.application.ugc.shorts.dto.CreatePipelineRunRequest
import com.ongo.application.ugc.shorts.dto.HookSelectionRequest
import com.ongo.application.ugc.shorts.dto.PipelineRunDetailResponse
import com.ongo.application.ugc.shorts.dto.PipelineRunListResponse
import com.ongo.application.ugc.shorts.dto.PipelineRunResponse
import com.ongo.application.ugc.shorts.dto.RunStageResponse
import com.ongo.application.ugc.shorts.dto.ScheduleConfirmRequest
import com.ongo.application.ugc.shorts.dto.ShortsClipResponse
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.shorts.ClipHookRepository
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.HookVariant
import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.PipelineRunStatus
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.RunStageRepository
import com.ongo.domain.ugc.shorts.ShortsClip
import com.ongo.domain.ugc.shorts.ShortsClipRepository
import com.ongo.domain.ugc.shorts.ShortsTemplate
import com.ongo.domain.ugc.shorts.ShortsTemplateRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.workspace.WorkspaceRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 쇼츠 파이프라인 유스케이스. 실행 생성/조회/재실행/후킹 선택/예약 확정/산출물 다운로드를 담당한다.
 * 실제 단계 실행은 이벤트 → 리스너 → 오케스트레이터가 비동기로 처리한다.
 */
@Service
class ShortsPipelineUseCase(
    private val pipelineRunRepository: PipelineRunRepository,
    private val runStageRepository: RunStageRepository,
    private val shortsClipRepository: ShortsClipRepository,
    private val clipHookRepository: ClipHookRepository,
    private val shortsTemplateRepository: ShortsTemplateRepository,
    private val videoRepository: VideoRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val renderSpecBuilder: ShortsRenderSpecBuilder,
    private val eventPublisher: ApplicationEventPublisher,
) {

    private val mapper = jacksonObjectMapper()

    /** 실행 생성: 원본 영상 확인 → PENDING 저장 → TRANSCRIBE부터 이벤트 발행. */
    @Transactional
    fun createRun(userId: Long, workspaceId: Long, request: CreatePipelineRunRequest): PipelineRunResponse {
        assertWorkspaceAccess(userId, workspaceId)

        val video = videoRepository.findById(request.sourceVideoId)
            ?: throw BusinessException("SHORTS_SOURCE_VIDEO_NOT_FOUND", "원본 영상을 찾을 수 없습니다: ${request.sourceVideoId}")
        if (video.userId != userId) {
            throw BusinessException("ACCESS_DENIED", "해당 영상에 접근 권한이 없습니다")
        }

        val run = pipelineRunRepository.save(
            PipelineRun(
                workspaceId = workspaceId,
                userId = userId,
                sourceVideoId = request.sourceVideoId,
                templateId = request.templateId,
                status = PipelineRunStatus.PENDING,
            ),
        )

        eventPublisher.publishEvent(ShortsPipelineEvent(runId = run.id, fromStage = PipelineStage.TRANSCRIBE))
        return run.toResponse(video.title)
    }

    /** 실행 목록 (페이지네이션). */
    fun listRuns(userId: Long, workspaceId: Long, page: Int, size: Int): PipelineRunListResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val runs = pipelineRunRepository.findByWorkspace(workspaceId, offset = page * size, limit = size)
        val total = pipelineRunRepository.countByWorkspace(workspaceId)
        val titles = videoRepository.findByIds(runs.map { it.sourceVideoId })
            .mapNotNull { video -> video.id?.let { it to video.title } }
            .toMap()
        return PipelineRunListResponse(
            runs = runs.map { it.toResponse(titles[it.sourceVideoId]) },
            total = total,
            page = page,
            size = size,
        )
    }

    /** 실행 상세 (단계 + 클립 + 후킹 포함). */
    fun getRunDetail(userId: Long, workspaceId: Long, runId: Long): PipelineRunDetailResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val run = loadRunInWorkspace(workspaceId, runId)
        return buildDetail(run)
    }

    /**
     * 단계 재실행: 그 단계와 이후 단계 기록을 지우고 해당 단계부터 다시 돌린다.
     * - SEGMENT 이전부터: 후킹 + 클립 전부 삭제, clipCount=0
     * - HOOK부터: 후킹만 삭제하고 클립은 DRAFT/scheduledAt=null로
     * - 그 외(SUBTITLE/TEMPLATE/RENDER_SPEC/VALIDATE): 실행기가 덮어쓴다
     */
    @Transactional
    fun rerunStage(userId: Long, workspaceId: Long, runId: Long, stageName: String): PipelineRunResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val run = loadRunInWorkspace(workspaceId, runId)

        if (run.status == PipelineRunStatus.RUNNING) {
            throw BusinessException("SHORTS_RUN_INVALID_STATE", "실행 중에는 재실행할 수 없습니다")
        }
        val stage = parseStage(stageName)
        if (stage == PipelineStage.SCHEDULE) {
            throw BusinessException("SHORTS_STAGE_NOT_RERUNNABLE", "예약 단계는 재실행할 수 없습니다. 예약 확정을 이용해 주세요")
        }

        runStageRepository.deleteFrom(runId, stage.sortOrder)

        val clips = shortsClipRepository.findByRunId(runId)
        when {
            stage.sortOrder <= PipelineStage.SEGMENT.sortOrder -> {
                clipHookRepository.deleteByClipIds(clips.map { it.id })
                shortsClipRepository.deleteByRunId(runId)
                pipelineRunRepository.update(run.copy(clipCount = 0))
            }
            stage == PipelineStage.HOOK -> {
                clipHookRepository.deleteByClipIds(clips.map { it.id })
                clips.forEach { clip ->
                    shortsClipRepository.update(clip.copy(status = ClipStatus.DRAFT, scheduledAt = null))
                }
            }
            else -> Unit // 나머지 단계는 실행기가 결과를 덮어쓴다
        }

        val reset = pipelineRunRepository.update(
            pipelineRunRepository.findById(runId)!!.copy(
                status = PipelineRunStatus.PENDING,
                currentStage = null,
                errorMessage = null,
            ),
        )
        eventPublisher.publishEvent(ShortsPipelineEvent(runId = runId, fromStage = stage))
        val video = videoRepository.findById(reset.sourceVideoId)
        return reset.toResponse(video?.title)
    }

    /** 후킹 일괄 선택: A/B 또는 CUSTOM(직접 입력)을 확정하고 TEMPLATE부터 이어 달린다. */
    @Transactional
    fun selectHooks(userId: Long, workspaceId: Long, runId: Long, request: HookSelectionRequest): PipelineRunDetailResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val run = loadRunInWorkspace(workspaceId, runId)

        if (run.status != PipelineRunStatus.AWAITING_HOOK_SELECTION) {
            throw BusinessException("SHORTS_RUN_INVALID_STATE", "후킹 선택 대기 상태가 아닙니다: ${run.status}")
        }

        val clips = shortsClipRepository.findByRunId(runId)
        val clipsById = clips.associateBy { it.id }
        val hooksByClip = clipHookRepository.findByClipIds(clips.map { it.id }).groupBy { it.clipId }

        request.selections.forEach { selection ->
            val clip = clipsById[selection.clipId]
                ?: throw BusinessException("SHORTS_CLIP_NOT_FOUND", "이 실행의 클립이 아닙니다: ${selection.clipId}")

            clipHookRepository.clearSelection(clip.id)
            val text = selection.customText
                ?: hooksByClip[clip.id]?.firstOrNull { it.variant == selection.variant }?.text
                ?: throw BusinessException("SHORTS_RUN_INVALID_STATE", "선택할 후킹 문구가 없습니다: clipId=${clip.id}, variant=${selection.variant}")
            clipHookRepository.markSelected(clip.id, selection.variant, text)
            shortsClipRepository.update(clip.copy(status = ClipStatus.HOOK_SELECTED))
        }

        request.discardClipIds.forEach { clipId ->
            val clip = clipsById[clipId]
                ?: throw BusinessException("SHORTS_CLIP_NOT_FOUND", "이 실행의 클립이 아닙니다: $clipId")
            shortsClipRepository.update(clip.copy(status = ClipStatus.DISCARDED))
        }

        pipelineRunRepository.update(
            pipelineRunRepository.findById(runId)!!.copy(status = PipelineRunStatus.PENDING, errorMessage = null),
        )
        eventPublisher.publishEvent(ShortsPipelineEvent(runId = runId, fromStage = PipelineStage.TEMPLATE))

        return buildDetail(pipelineRunRepository.findById(runId)!!)
    }

    /** 예약 확정: SCHEDULE 단계만 파라미터와 함께 실행한다. */
    @Transactional
    fun confirmSchedule(userId: Long, workspaceId: Long, runId: Long, request: ScheduleConfirmRequest): PipelineRunResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val run = loadRunInWorkspace(workspaceId, runId)

        if (run.status != PipelineRunStatus.AWAITING_SCHEDULE) {
            throw BusinessException("SHORTS_RUN_INVALID_STATE", "예약 확정 대기 상태가 아닙니다: ${run.status}")
        }
        if (request.intervalHours <= 0) {
            throw BusinessException("SHORTS_RUN_INVALID_STATE", "예약 간격은 1시간 이상이어야 합니다")
        }

        val updated = pipelineRunRepository.update(
            run.copy(status = PipelineRunStatus.PENDING, errorMessage = null),
        )
        eventPublisher.publishEvent(
            ShortsPipelineEvent(
                runId = runId,
                fromStage = PipelineStage.SCHEDULE,
                scheduleStartAt = request.startAt,
                scheduleIntervalHours = request.intervalHours,
                platforms = request.platforms,
            ),
        )
        val video = videoRepository.findById(updated.sourceVideoId)
        return updated.toResponse(video?.title)
    }

    /**
     * 서버 렌더를 사용할 수 없는 환경에서 외부에서 만든 완성 영상을 클립에 연결하는 보완 경로.
     * 일반적인 Compose/쇼츠 실행 흐름은 서버 렌더가 만든 videoId를 자동으로 연결한다.
     */
    @Transactional
    fun attachRenderedVideo(
        userId: Long,
        workspaceId: Long,
        runId: Long,
        clipId: Long,
        videoId: Long,
    ): ShortsClipResponse {
        assertWorkspaceAccess(userId, workspaceId)
        loadRunInWorkspace(workspaceId, runId)

        val clip = shortsClipRepository.findById(clipId)
            ?: throw BusinessException("SHORTS_CLIP_NOT_FOUND", "클립을 찾을 수 없습니다: $clipId")
        if (clip.runId != runId) {
            throw BusinessException("SHORTS_CLIP_NOT_FOUND", "이 실행의 클립이 아닙니다: $clipId")
        }
        if (clip.status == ClipStatus.DISCARDED) {
            throw BusinessException("SHORTS_RUN_INVALID_STATE", "제외된 클립에는 영상을 연결할 수 없습니다")
        }

        val video = videoRepository.findById(videoId)
            ?: throw BusinessException("SHORTS_SOURCE_VIDEO_NOT_FOUND", "영상을 찾을 수 없습니다: $videoId")
        if (video.userId != userId) {
            throw BusinessException("ACCESS_DENIED", "해당 영상에 접근 권한이 없습니다")
        }

        val updated = shortsClipRepository.update(
            clip.copy(renderedVideoId = videoId, status = ClipStatus.RENDERED),
        )
        val hooks = clipHookRepository.findByClipIds(listOf(clipId))
        return updated.toResponse(hooks)
    }

    /** 클립의 render-spec.json 문자열을 반환한다. */
    fun getRenderSpec(userId: Long, workspaceId: Long, runId: Long, clipId: Long): String {
        assertWorkspaceAccess(userId, workspaceId)
        loadRunInWorkspace(workspaceId, runId)
        val clip = shortsClipRepository.findById(clipId)
        if (clip == null || clip.runId != runId || clip.renderSpec == null) {
            throw BusinessException("SHORTS_CLIP_NOT_FOUND", "렌더 스펙을 찾을 수 없습니다: clipId=$clipId")
        }
        return clip.renderSpec!!
    }

    /** 렌더 산출물 3종(render-spec.json, clip-{seq}.ass, render.sh)을 클립별로 묶은 zip을 만든다. */
    fun getRenderBundle(userId: Long, workspaceId: Long, runId: Long): ByteArray {
        assertWorkspaceAccess(userId, workspaceId)
        val run = loadRunInWorkspace(workspaceId, runId)

        val clips = shortsClipRepository.findByRunId(runId)
            .filter { it.status != ClipStatus.DISCARDED && it.renderSpec != null }
        if (clips.isEmpty()) {
            throw BusinessException("SHORTS_CLIP_NOT_FOUND", "다운로드할 렌더 산출물이 없습니다")
        }

        val template = resolveTemplate(run)

        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            clips.forEach { clip ->
                val specJson = clip.renderSpec!!
                val spec = renderSpecBuilder.parseSpec(specJson)
                val prefix = "clip-${clip.seq}"

                zip.putNextEntry(ZipEntry("$prefix/render-spec.json"))
                zip.write(specJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("$prefix/clip-${clip.seq}.ass"))
                zip.write(renderSpecBuilder.buildAss(spec, template).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("$prefix/render.sh"))
                zip.write(renderSpecBuilder.buildRenderScript(spec).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }

    /** 실행 취소/삭제. RUNNING이면 CANCELLED로 표시해 리스너가 협조적 중단하게 한 뒤 삭제한다. */
    @Transactional
    fun deleteRun(userId: Long, workspaceId: Long, runId: Long) {
        assertWorkspaceAccess(userId, workspaceId)
        val run = loadRunInWorkspace(workspaceId, runId)
        if (run.status == PipelineRunStatus.RUNNING) {
            pipelineRunRepository.update(run.copy(status = PipelineRunStatus.CANCELLED))
        }
        pipelineRunRepository.delete(runId)
    }

    // ---- 날부 헬퍼 ----

    private fun assertWorkspaceAccess(userId: Long, workspaceId: Long) {
        val accessible = workspaceRepository.findAccessibleByUserId(userId).any { it.id == workspaceId }
        if (!accessible) throw NotFoundException("워크스페이스", workspaceId)
    }

    private fun loadRunInWorkspace(workspaceId: Long, runId: Long): PipelineRun {
        val run = pipelineRunRepository.findById(runId)
            ?: throw BusinessException("SHORTS_RUN_NOT_FOUND", "실행을 찾을 수 없습니다: $runId")
        if (run.workspaceId != workspaceId) {
            throw BusinessException("ACCESS_DENIED", "다른 워크스페이스의 실행입니다")
        }
        return run
    }

    private fun parseStage(stageName: String): PipelineStage =
        runCatching { PipelineStage.valueOf(stageName.uppercase()) }.getOrElse {
            throw BusinessException("SHORTS_RUN_INVALID_STATE", "알 수 없는 파이프라인 단계입니다: $stageName")
        }

    private fun resolveTemplate(run: PipelineRun): ShortsTemplate? =
        run.templateId?.let { shortsTemplateRepository.findById(it) }
            ?: shortsTemplateRepository.findByWorkspace(run.workspaceId).firstOrNull { it.isDefault }

    private fun buildDetail(run: PipelineRun): PipelineRunDetailResponse {
        val stages = runStageRepository.findByRunId(run.id)
            .sortedBy { it.stage.sortOrder }
            .map { it.toResponse() }
        val clips = shortsClipRepository.findByRunId(run.id)
        val hooksByClip = clipHookRepository.findByClipIds(clips.map { it.id }).groupBy { it.clipId }
        val video = videoRepository.findById(run.sourceVideoId)
        return PipelineRunDetailResponse(
            run = run.toResponse(video?.title),
            stages = stages,
            clips = clips.map { it.toResponse(hooksByClip[it.id].orEmpty()) },
        )
    }

    // ---- 매핑 ----

    private fun PipelineRun.toResponse(sourceVideoTitle: String?) = PipelineRunResponse(
        id = id,
        sourceVideoId = sourceVideoId,
        sourceVideoTitle = sourceVideoTitle,
        templateId = templateId,
        status = status.name,
        currentStage = currentStage?.name,
        clipCount = clipCount,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun com.ongo.domain.ugc.shorts.RunStage.toResponse() = RunStageResponse(
        stage = stage.name,
        status = status.name,
        promptId = promptId,
        promptRevision = promptRevision,
        aiProvider = aiProvider,
        creditCost = creditCost,
        errorMessage = errorMessage,
        startedAt = startedAt,
        completedAt = completedAt,
    )

    private fun ShortsClip.toResponse(hooks: List<com.ongo.domain.ugc.shorts.ClipHook>) = ShortsClipResponse(
        id = id,
        seq = seq,
        startMs = startMs,
        endMs = endMs,
        durationMs = endMs - startMs,
        title = title,
        caption = caption,
        status = status.name,
        scheduledAt = scheduledAt,
        hooks = hooks.map {
            ClipHookResponse(id = it.id, variant = it.variant.name, text = it.text, selected = it.selected)
        },
        subtitleCount = countSubtitles(subtitleJson),
        hasRenderSpec = renderSpec != null,
    )

    private fun countSubtitles(subtitleJson: String?): Int {
        if (subtitleJson.isNullOrBlank()) return 0
        return runCatching { mapper.readTree(subtitleJson).size() }.getOrDefault(0)
    }
}
