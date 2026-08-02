package com.ongo.application.ugc.shorts.stage

import com.ongo.application.ugc.shorts.ShortsPromptDefaults
import com.ongo.domain.ugc.shorts.ClipHook
import com.ongo.domain.ugc.shorts.HookVariant
import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.ShortsClip
import com.ongo.domain.ugc.shorts.ShortsPrompt
import com.ongo.domain.ugc.shorts.ShortsPromptRepository
import com.ongo.domain.ugc.shorts.ShortsTemplate
import java.time.Instant

/** 전사 세그먼트 (밀리초 기준). STT 결과의 초 단위를 변환한 것이다. */
data class TranscriptSegmentMs(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

/** 예약 확정 파라미터. SCHEDULE 단계에서만 사용한다. */
data class ScheduleParams(
    val startAt: Instant,
    val intervalHours: Int,
    val platforms: List<String>,
)

/** SEGMENT 단계 AI가 뽑은 클립 후보. 실제 ShortsClip 저장은 오케스트레이터가 한다. */
data class ClipCandidate(
    val title: String?,
    val caption: String?,
    val startMs: Long,
    val endMs: Long,
)

/** HOOK 단계 AI가 만든 클립별 후킹 문구 하나. */
data class GeneratedHook(
    val clipId: Long,
    val variant: HookVariant,
    val text: String,
)

/**
 * 단계 실행에 필요한 모든 입력. 오케스트레이터가 만들고, 단계 출력이 반영될 때마다 갱신한다.
 */
class ShortsStageContext(
    var run: PipelineRun,
    val userId: Long,
    val workspaceId: Long,
    val sourceVideoTitle: String?,
    val sourceFileUrl: String?,
    var transcriptText: String? = null,
    var transcriptSegments: List<TranscriptSegmentMs> = emptyList(),
    var clips: List<ShortsClip> = emptyList(),
    var hooks: Map<Long, List<ClipHook>> = emptyMap(),
    var cropJson: String? = null,
    var template: ShortsTemplate? = null,
    val schedule: ScheduleParams? = null,
)

/**
 * 단계 실행 결과. outputSnapshot만 필수이고, 나머지는 단계별로 채운다.
 * DB 반영(클립/후킹/실행 갱신)은 전부 오케스트레이터가 한다.
 */
data class ShortsStageOutput(
    val outputSnapshot: String,
    val inputSnapshot: String? = null,
    val promptId: Long? = null,
    val promptRevision: Int? = null,
    // TRANSCRIBE
    val transcriptText: String? = null,
    val transcriptSegments: List<TranscriptSegmentMs>? = null,
    // REFRAME
    val cropJson: String? = null,
    // SEGMENT
    val clipCandidates: List<ClipCandidate>? = null,
    // SUBTITLE: clipId -> subtitle JSON 배열 문자열
    val subtitles: Map<Long, String>? = null,
    // HOOK
    val hooks: List<GeneratedHook>? = null,
    // RENDER_SPEC: clipId -> render-spec JSON 문자열
    val renderSpecs: Map<Long, String>? = null,
    // SCHEDULE: clipId -> 예약 시각
    val scheduledAts: Map<Long, Instant>? = null,
)

/** 파이프라인 단계 실행기. 한 단계 = 한 구현체. */
interface ShortsStageExecutor {
    val stage: PipelineStage
    fun execute(context: ShortsStageContext): ShortsStageOutput
}

/** 단계 프롬프트 로딩: 워크스페이스 오버라이드 → 시스템 기본값 → 폴팩 상수 순. */
internal fun loadStagePrompt(
    shortsPromptRepository: ShortsPromptRepository,
    workspaceId: Long,
    stage: PipelineStage,
): ShortsPrompt =
    shortsPromptRepository.findByWorkspaceAndStage(workspaceId, stage)
        ?: shortsPromptRepository.findDefaultByStage(stage)
        ?: ShortsPromptDefaults.fallback(stage)

/** 프롬프트 id는 폴팩 상수(id=0)일 때 기록하지 않는다. */
internal fun ShortsPrompt.recordableId(): Long? = id.takeIf { it > 0 }
