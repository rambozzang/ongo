package com.ongo.application.ugc.shorts

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ugc.shorts.dto.ClipEditRequest
import com.ongo.application.ugc.shorts.dto.ClipEditResponse
import com.ongo.application.ugc.shorts.dto.ClipEditorResponse
import com.ongo.application.ugc.shorts.dto.EditorSubtitleLine
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.shorts.ClipHookRepository
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.PipelineRunStatus
import com.ongo.domain.ugc.shorts.ShortsClip
import com.ongo.domain.ugc.shorts.ShortsClipRepository
import com.ongo.domain.ugc.shorts.ShortsRenderJobRepository
import com.ongo.domain.ugc.shorts.ShortsRenderJobStatus
import com.ongo.domain.ugc.shorts.ShortsTemplateRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.workspace.WorkspaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 클립 편집기: 구간 다듬기·자막 줄 수정·제목/설명 수정·크롭 조정.
 *
 * 편집은 AI 를 부르지 않으므로 크레딧을 받지 않는다. 다시 렌더는 기존 렌더 API 를 쓴다.
 *
 * ## 낡은 결과를 남기지 않는다
 * 화면을 바꾸는 편집(구간·자막·크롭)을 저장하면 이전 렌더 결과는 더 이상 이 클립이 아니다.
 * 렌더 지시서를 새로 만들고, 끝난 렌더 작업과 완성 영상 연결을 지워 "다시 렌더"가 실제로
 * 새로 인코딩하게 한다. 제목·설명만 고치면 영상은 그대로이므로 렌더를 무효화하지 않는다.
 *
 * ## 편집할 수 없는 때
 * 파이프라인이 실행 중이면 단계가 같은 클립을 덮어쓰고, 렌더 중이면 인코더가 이전 지시서를 읽고 있고,
 * 예약·게시가 끝났으면 바뀐 내용이 이미 나간 게시물과 어긋난다. 이때는 거절한다.
 */
@Service
class ShortsClipEditUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val pipelineRunRepository: PipelineRunRepository,
    private val shortsClipRepository: ShortsClipRepository,
    private val clipHookRepository: ClipHookRepository,
    private val renderJobRepository: ShortsRenderJobRepository,
    private val shortsTemplateRepository: ShortsTemplateRepository,
    private val videoRepository: VideoRepository,
    private val renderSpecBuilder: ShortsRenderSpecBuilder,
) {
    private val mapper = jacksonObjectMapper()

    fun getEditor(userId: Long, workspaceId: Long, runId: Long, clipId: Long): ClipEditorResponse {
        val (run, clip) = load(userId, workspaceId, runId, clipId)
        return editorOf(run, clip)
    }

    @Transactional
    fun edit(userId: Long, workspaceId: Long, runId: Long, clipId: Long, request: ClipEditRequest): ClipEditResponse {
        val (run, clip) = load(userId, workspaceId, runId, clipId)
        notEditableReason(run, clip)?.let { throw BusinessException("SHORTS_CLIP_NOT_EDITABLE", it) }

        var updated = clip
        var uncovered = false

        // 1) 구간 — 자막을 같은 양만큼 옮긴다.
        if (request.startMs != null || request.endMs != null) {
            val start = request.startMs ?: clip.startMs
            val end = request.endMs ?: clip.endMs
            if (start != clip.startMs || end != clip.endMs) {
                ShortsClipEditPolicy.validateRange(start, end, run.sourceDurationMs)
                val shifted = ShortsClipEditPolicy.shiftSubtitles(
                    renderSpecBuilder.parseSubtitles(clip.subtitleJson), clip.startMs, start, end,
                )
                uncovered = ShortsClipEditPolicy.hasUncoveredSpan(shifted, end - start)
                updated = updated.copy(
                    startMs = start,
                    endMs = end,
                    subtitleJson = clip.subtitleJson?.let { ShortsClipEditPolicy.toJson(shifted) },
                )
            }
        }

        // 2) 자막 줄 — 다듬은 길이로 검사한다.
        request.subtitles?.let { edits ->
            val lines = ShortsClipEditPolicy.applyLineEdits(
                edits.map { ShortsClipEditPolicy.LineEdit(it.startMs, it.endMs, it.text) },
                renderSpecBuilder.parseSubtitles(updated.subtitleJson),
                updated.endMs - updated.startMs,
            )
            uncovered = false // 사용자가 직접 정한 자막이다
            updated = updated.copy(subtitleJson = ShortsClipEditPolicy.toJson(lines))
        }

        // 3) 제목·설명 — 빈 값은 "없음"으로 저장한다(게시 때 원본 제목으로 대신한다).
        ShortsClipEditPolicy.validateText(request.title, request.caption)
        request.title?.let { updated = updated.copy(title = it.trim().ifEmpty { null }) }
        request.caption?.let { updated = updated.copy(caption = it.trim().ifEmpty { null }) }

        // 4) 크롭 — 정규화는 ShortsCropPolicy 한 곳에서만.
        request.crop?.let { crop ->
            updated = updated.copy(
                cropJson = ShortsCropPolicy.normalizeJson(
                    mapper.writeValueAsString(crop), request.sourceWidth, request.sourceHeight,
                ),
            )
        }

        // 5) 플랫폼별 메타 — 영상과 무관하므로 렌더를 무효화하지 않는다.
        request.platformMeta?.let { entries ->
            val validated = ShortsPlatformMeta.validateEdit(
                entries.map { ShortsPlatformMeta.Entry(it.platform, it.title, it.description, it.hashtags) },
            )
            updated = updated.copy(platformMetaJson = ShortsPlatformMeta.toJson(validated))
        }

        val renderChanged = updated.startMs != clip.startMs ||
            updated.endMs != clip.endMs ||
            updated.subtitleJson != clip.subtitleJson ||
            updated.cropJson != clip.cropJson

        if (renderChanged && clip.renderSpec != null) {
            val hook = clipHookRepository.findByClipIds(listOf(clip.id)).firstOrNull { it.selected }
            val spec = renderSpecBuilder.buildSpec(
                clip = updated,
                sourceVideoId = run.sourceVideoId,
                sourceFileUrl = videoRepository.findById(run.sourceVideoId)?.fileUrl,
                hook = hook,
                template = resolveTemplate(run),
            )
            updated = updated.copy(
                renderSpec = renderSpecBuilder.toJson(spec),
                status = ClipStatus.RENDER_READY,
                renderedVideoId = null,
            )
        }

        val saved = shortsClipRepository.update(updated)

        /*
         * 클립을 먼저 바꾸고 그 다음 끝난 렌더 작업을 지운다. 그 사이 누가 렌더를 시작했다면
         * 조건부 삭제가 실패하고, 예외로 이 트랜잭션 전체가 되돌아간다 — 인코더가 읽는 지시서와
         * 저장된 클립이 어긋난 채로 남지 않는다.
         */
        val discardedRender = renderChanged && clip.renderSpec != null
        if (discardedRender && !renderJobRepository.deleteFinished(run.id, clip.id)) {
            throw BusinessException("SHORTS_CLIP_NOT_EDITABLE", RENDERING_REASON)
        }

        return ClipEditResponse(
            editor = editorOf(run, saved),
            needsRerender = discardedRender && (clip.renderedVideoId != null || clip.status == ClipStatus.RENDERED),
            uncoveredSubtitleSpan = uncovered,
        )
    }

    private fun load(userId: Long, workspaceId: Long, runId: Long, clipId: Long): Pair<PipelineRun, ShortsClip> {
        if (workspaceRepository.findAccessibleByUserId(userId).none { it.id == workspaceId }) {
            throw NotFoundException("워크스페이스", workspaceId)
        }
        // 다른 워크스페이스의 실행·클립은 존재 여부도 숨긴다.
        val run = pipelineRunRepository.findById(runId)?.takeIf { it.workspaceId == workspaceId }
            ?: throw NotFoundException("파이프라인 실행", runId)
        val clip = shortsClipRepository.findById(clipId)?.takeIf { it.runId == runId }
            ?: throw NotFoundException("클립", clipId)
        return run to clip
    }

    private fun notEditableReason(run: PipelineRun, clip: ShortsClip): String? = when {
        run.status == PipelineRunStatus.RUNNING -> "파이프라인이 실행 중이라 편집할 수 없습니다. 끝난 뒤 다시 시도해 주세요"
        clip.status == ClipStatus.SCHEDULED || clip.status == ClipStatus.PUBLISHED ->
            "이미 예약·게시된 클립은 편집할 수 없습니다. 바뀐 내용이 나간 게시물과 어긋납니다"
        clip.status == ClipStatus.DISCARDED -> "버린 클립은 편집할 수 없습니다"
        renderJobRepository.findByRunAndClip(run.id, clip.id)?.status.let {
            it == ShortsRenderJobStatus.QUEUED || it == ShortsRenderJobStatus.RUNNING
        } -> RENDERING_REASON
        else -> null
    }

    private fun editorOf(run: PipelineRun, clip: ShortsClip): ClipEditorResponse {
        val reason = notEditableReason(run, clip)
        return ClipEditorResponse(
            clipId = clip.id,
            runId = run.id,
            seq = clip.seq,
            startMs = clip.startMs,
            endMs = clip.endMs,
            title = clip.title,
            caption = clip.caption,
            status = clip.status.name,
            subtitles = renderSpecBuilder.parseSubtitles(clip.subtitleJson).map {
                EditorSubtitleLine(it.startMs, it.endMs, it.text, hasWordTiming = it.words.isNotEmpty())
            },
            cropJson = clip.cropJson,
            sourceDurationMs = run.sourceDurationMs,
            editable = reason == null,
            notEditableReason = reason,
            hasRenderedVideo = clip.renderedVideoId != null,
            platformMeta = ShortsPlatformMeta.parse(clip.platformMetaJson).map { it.toResponse() },
        )
    }

    private fun resolveTemplate(run: PipelineRun) =
        run.templateId?.let { shortsTemplateRepository.findById(it) }
            ?: shortsTemplateRepository.findByWorkspace(run.workspaceId).firstOrNull { it.isDefault }

    private companion object {
        const val RENDERING_REASON = "렌더 중인 클립은 편집할 수 없습니다. 렌더가 끝난 뒤 다시 시도해 주세요"
    }
}
