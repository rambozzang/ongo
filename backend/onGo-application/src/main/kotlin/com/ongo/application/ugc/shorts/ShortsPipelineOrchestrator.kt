package com.ongo.application.ugc.shorts

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.credit.CreditService
import com.ongo.application.ugc.shorts.stage.ClipCandidate
import com.ongo.application.ugc.shorts.stage.ScheduleParams
import com.ongo.application.ugc.shorts.stage.ShortsStageContext
import com.ongo.application.ugc.shorts.stage.ShortsStageExecutor
import com.ongo.application.ugc.shorts.stage.ShortsStageOutput
import com.ongo.application.ugc.shorts.stage.ScheduleOutcome
import com.ongo.application.ugc.shorts.stage.TranscriptSegmentMs
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.AiProvider
import com.ongo.domain.settings.UserSettingsRepository
import com.ongo.domain.ugc.shorts.ClipHook
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.ClipHookRepository
import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.PipelineRunStatus
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.RunStage
import com.ongo.domain.ugc.shorts.RunStageRepository
import com.ongo.domain.ugc.shorts.RunStageStatus
import com.ongo.domain.ugc.shorts.ShortsClip
import com.ongo.domain.ugc.shorts.ShortsClipRepository
import com.ongo.domain.ugc.shorts.ShortsTemplate
import com.ongo.domain.ugc.shorts.ShortsTemplateRepository
import com.ongo.domain.video.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 쇼츠 파이프라인 오케스트레이터. fromStage부터 sortOrder 순으로 단계를 돌린다.
 *
 * - HOOK 완료 후 → AWAITING_HOOK_SELECTION 저장하고 종료 (1차 게이트)
 * - VALIDATE 완료 후 → AWAITING_SCHEDULE 저장하고 종료 (2차 게이트)
 * - SCHEDULE은 예약 파라미터가 있을 때만 실행하고 완료 시 COMPLETED
 * - 단계 실패 시 그 단계분 크레딧만 환불하고 실행을 FAILED로 끝낸다
 */
@Component
class ShortsPipelineOrchestrator(
    private val pipelineRunRepository: PipelineRunRepository,
    private val runStageRepository: RunStageRepository,
    private val shortsClipRepository: ShortsClipRepository,
    private val clipHookRepository: ClipHookRepository,
    private val shortsTemplateRepository: ShortsTemplateRepository,
    private val videoRepository: VideoRepository,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val userSettingsRepository: UserSettingsRepository,
    executors: List<ShortsStageExecutor>,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper()
    private val executorsByStage: Map<PipelineStage, ShortsStageExecutor> = executors.associateBy { it.stage }

    fun run(runId: Long, fromStage: PipelineStage, schedule: ScheduleParams? = null) {
        val loaded = pipelineRunRepository.findById(runId) ?: return
        if (loaded.status == PipelineRunStatus.CANCELLED) return

        pipelineRunRepository.update(loaded.copy(status = PipelineRunStatus.RUNNING, errorMessage = null))
        val context = buildContext(runId, schedule)

        val stages = PipelineStage.entries
            .sortedBy { it.sortOrder }
            .filter { it.sortOrder >= fromStage.sortOrder }

        for (stage in stages) {
            // SCHEDULE은 예약 파라미터가 있을 때만 실행한다
            if (stage == PipelineStage.SCHEDULE && schedule == null) break

            // 매 단계 시작 전 다시 읽어 CANCELLED면 협조적 중단
            val fresh = pipelineRunRepository.findById(runId) ?: return
            if (fresh.status == PipelineRunStatus.CANCELLED) return

            val run = pipelineRunRepository.update(
                fresh.copy(status = PipelineRunStatus.RUNNING, currentStage = stage, errorMessage = null),
            )
            context.run = run

            val output = executeStage(run, stage, context) ?: return

            when (stage) {
                PipelineStage.HOOK -> {
                    persistRun(run.id) { it.copy(status = PipelineRunStatus.AWAITING_HOOK_SELECTION) }
                    return
                }
                PipelineStage.VALIDATE -> {
                    persistRun(run.id) { it.copy(status = PipelineRunStatus.AWAITING_SCHEDULE) }
                    return
                }
                PipelineStage.SCHEDULE -> {
                    val outcome = output.scheduleOutcome
                    when (outcome) {
                        ScheduleOutcome.FAILED -> persistRun(run.id) {
                            it.copy(
                                status = PipelineRunStatus.FAILED,
                                errorMessage = "모든 쇼츠 플랫폼 게시에 실패했습니다. 게시 결과를 확인한 뒤 다시 시도해 주세요.",
                            )
                        }
                        ScheduleOutcome.PARTIAL -> persistRun(run.id) {
                            it.copy(
                                status = PipelineRunStatus.PARTIALLY_COMPLETED,
                                errorMessage = "일부 쇼츠 플랫폼 게시에 실패했습니다. 실행 상세에서 플랫폼별 결과를 확인해 주세요.",
                            )
                        }
                        else -> persistRun(run.id) { it.copy(status = PipelineRunStatus.COMPLETED) }
                    }
                    return
                }
                else -> Unit
            }
        }
    }

    /** 단계 하나를 실행한다. 실패하면 환불·FAILED 기록 후 null을 반환한다. */
    private fun executeStage(run: PipelineRun, stage: PipelineStage, context: ShortsStageContext): ShortsStageOutput? {
        val feature = FEATURE_BY_STAGE[stage]
        val executor = executorsByStage[stage]
            ?: error("단계 실행기가 없습니다: $stage")

        var runStage: RunStage? = null
        var deducted = false
        try {
            if (feature != null) {
                rateLimiter.checkRateLimit(run.userId)
                creditService.validateAndDeduct(run.userId, feature)
                deducted = true
            }

            runStage = runStageRepository.save(
                RunStage(
                    runId = run.id,
                    stage = stage,
                    status = RunStageStatus.RUNNING,
                    startedAt = Instant.now(),
                ),
            )

            val output = executor.execute(context)
            applyOutput(run, stage, context, output)

            runStageRepository.update(
                runStage.copy(
                    status = RunStageStatus.COMPLETED,
                    completedAt = Instant.now(),
                    creditCost = feature?.creditCost ?: 0,
                    promptId = output.promptId,
                    promptRevision = output.promptRevision,
                    aiProvider = if (feature != null) resolveProviderName(run.userId) else null,
                    inputSnapshot = output.inputSnapshot,
                    outputSnapshot = output.outputSnapshot,
                ),
            )
            return output
        } catch (e: Exception) {
            log.error("쇼츠 파이프라인 단계 실패: runId={}, stage={}", run.id, stage, e)
            // 단계 실패 시 그 단계분 크레딧만 환불한다 (차감이 실제로 일어났을 때만)
            if (deducted && feature != null) {
                runCatching { creditService.refundCredit(run.userId, feature.creditCost, feature.name) }
            }
            runStage?.let { saved ->
                runCatching {
                    runStageRepository.update(
                        saved.copy(
                            status = RunStageStatus.FAILED,
                            errorMessage = e.message,
                            completedAt = Instant.now(),
                        ),
                    )
                }
            }
            persistRun(run.id) {
                it.copy(
                    status = PipelineRunStatus.FAILED,
                    errorMessage = "${stage.displayName} 단계 실패: ${e.message}",
                )
            }
            return null
        }
    }

    /** 단계 출력을 컨텍스트와 DB에 반영한다. */
    private fun applyOutput(run: PipelineRun, stage: PipelineStage, context: ShortsStageContext, output: ShortsStageOutput) {
        when (stage) {
            PipelineStage.TRANSCRIBE -> {
                context.transcriptText = output.transcriptText
                context.transcriptSegments = output.transcriptSegments.orEmpty()
                persistRun(run.id) { it.copy(transcriptText = output.transcriptText) }
            }
            PipelineStage.REFRAME -> {
                context.cropJson = output.cropJson
                // 메모리에만 두면 HOOK 게이트에서 멈췄다 재개할 때 소실된다.
                // TEMPLATE 이 크롭 없으면 조기 반환하므로 세로 변환이 통째로 빠진다.
                persistRun(run.id) { it.copy(cropJson = output.cropJson) }
            }
            PipelineStage.SEGMENT -> {
                val candidates: List<ClipCandidate> = output.clipCandidates.orEmpty()
                val saved = shortsClipRepository.saveAll(
                    candidates.mapIndexed { index, candidate ->
                        ShortsClip(
                            runId = run.id,
                            seq = index + 1,
                            startMs = candidate.startMs,
                            endMs = candidate.endMs,
                            title = candidate.title,
                            caption = candidate.caption,
                            status = ClipStatus.DRAFT,
                            dedupKey = "${run.id}:${index + 1}",
                        )
                    },
                )
                context.clips = saved
                persistRun(run.id) { it.copy(clipCount = saved.size) }
            }
            PipelineStage.SUBTITLE -> {
                output.subtitles?.forEach { (clipId, subtitleJson) ->
                    updateClip(context, clipId) { it.copy(subtitleJson = subtitleJson) }
                }
            }
            PipelineStage.HOOK -> {
                val generated = output.hooks.orEmpty()
                if (generated.isNotEmpty()) {
                    val saved = clipHookRepository.saveAll(
                        generated.map { ClipHook(clipId = it.clipId, variant = it.variant, text = it.text) },
                    )
                    context.hooks = saved.groupBy { it.clipId }
                }
            }
            PipelineStage.TEMPLATE -> {
                // REFRAME 출력 크롭을 각 클립에 기록한다
                val crop = context.cropJson ?: return
                context.clips.forEach { clip ->
                    if (clip.status != ClipStatus.DISCARDED) {
                        updateClip(context, clip.id) { it.copy(cropJson = crop) }
                    }
                }
            }
            PipelineStage.RENDER_SPEC -> {
                output.renderSpecs?.forEach { (clipId, renderSpec) ->
                    updateClip(context, clipId) { it.copy(renderSpec = renderSpec, status = ClipStatus.RENDER_READY) }
                }
            }
            PipelineStage.VALIDATE -> Unit // 검증 결과는 스냅샷에만 남긴다
            PipelineStage.SCHEDULE -> {
                output.scheduledAts?.forEach { (clipId, scheduledAt) ->
                    updateClip(context, clipId) { it.copy(scheduledAt = scheduledAt, status = ClipStatus.SCHEDULED) }
                }
            }
        }
    }

    private fun updateClip(context: ShortsStageContext, clipId: Long, transform: (ShortsClip) -> ShortsClip) {
        val clip = context.clips.find { it.id == clipId } ?: return
        val updated = shortsClipRepository.update(transform(clip))
        context.clips = context.clips.map { if (it.id == clipId) updated else it }
    }

    /** 실행을 다시 읽어 갱신하고 컨텍스트에도 반영한다 (낙관적 락 충돌 방지를 위해 항상 재조회). */
    private fun persistRun(runId: Long, transform: (PipelineRun) -> PipelineRun): PipelineRun? {
        val current = pipelineRunRepository.findById(runId) ?: return null
        return pipelineRunRepository.update(transform(current))
    }

    private fun buildContext(runId: Long, schedule: ScheduleParams?): ShortsStageContext {
        val run = pipelineRunRepository.findById(runId)
            ?: throw IllegalStateException("실행을 찾을 수 없습니다: $runId")
        val video = videoRepository.findById(run.sourceVideoId)
        val clips = shortsClipRepository.findByRunId(runId)
        val hooks = clipHookRepository.findByClipIds(clips.map { it.id }).groupBy { it.clipId }
        return ShortsStageContext(
            run = run,
            userId = run.userId,
            workspaceId = run.workspaceId,
            sourceVideoTitle = video?.title,
            sourceFileUrl = video?.fileUrl,
            transcriptText = run.transcriptText,
            cropJson = run.cropJson,
            transcriptSegments = loadTranscriptSegments(runId),
            clips = clips,
            hooks = hooks,
            template = resolveTemplate(run),
            schedule = schedule,
        )
    }

    /** TRANSCRIBE 단계 스냅샷에서 전사 세그먼트를 복원한다 (재실행 시에도 세그먼트가 필요하므로). */
    private fun loadTranscriptSegments(runId: Long): List<TranscriptSegmentMs> {
        val snapshot = runStageRepository
            .findByRunIdAndStage(runId, PipelineStage.TRANSCRIBE)
            ?.outputSnapshot
            ?: return emptyList()
        return runCatching {
            mapper.readTree(snapshot).path("segments").map { node ->
                TranscriptSegmentMs(
                    startMs = node.path("startMs").asLong(),
                    endMs = node.path("endMs").asLong(),
                    text = node.path("text").asText(),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 실행에 지정된 템플릿, 없으면 워크스페이스 기본 템플릿을 고른다. */
    private fun resolveTemplate(run: PipelineRun): ShortsTemplate? =
        run.templateId?.let { shortsTemplateRepository.findById(it) }
            ?: shortsTemplateRepository.findByWorkspace(run.workspaceId).firstOrNull { it.isDefault }

    /** 실제 사용된 AI 제공자명 (UserSettings.defaultAiProvider, 없으면 QWEN). */
    private fun resolveProviderName(userId: Long): String =
        userSettingsRepository.findByUserId(userId)?.defaultAiProvider?.name ?: AiProvider.QWEN.name

    companion object {
        /** AI(크레딧) 단계 ↔ AiFeature 매핑. RENDER_SPEC/SCHEDULE은 차감 없음. */
        private val FEATURE_BY_STAGE: Map<PipelineStage, AiFeature> = mapOf(
            PipelineStage.TRANSCRIBE to AiFeature.STT,
            PipelineStage.REFRAME to AiFeature.SHORTS_REFRAME,
            PipelineStage.SEGMENT to AiFeature.SHORTS_SEGMENT,
            PipelineStage.SUBTITLE to AiFeature.SHORTS_SUBTITLE,
            PipelineStage.HOOK to AiFeature.SHORTS_HOOK,
            PipelineStage.TEMPLATE to AiFeature.SHORTS_TEMPLATE,
            PipelineStage.VALIDATE to AiFeature.SHORTS_VALIDATE,
        )
    }
}
