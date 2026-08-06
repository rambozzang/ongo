package com.ongo.application.ugc.shorts

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.credit.CreditService
import com.ongo.application.ugc.shorts.stage.ClipCandidate
import com.ongo.application.ugc.shorts.stage.GeneratedHook
import com.ongo.application.ugc.shorts.stage.RenderSpecStageExecutor
import com.ongo.application.ugc.shorts.stage.ScheduleParams
import com.ongo.application.ugc.shorts.stage.ScheduleStageExecutor
import com.ongo.application.ugc.shorts.stage.ShortsStageContext
import com.ongo.application.ugc.shorts.stage.ShortsStageExecutor
import com.ongo.application.ugc.shorts.stage.ShortsStageOutput
import com.ongo.application.ugc.shorts.stage.TranscriptSegmentMs
import com.ongo.common.enums.AiFeature
import com.ongo.domain.settings.UserSettingsRepository
import com.ongo.domain.ugc.shorts.ClipHook
import com.ongo.domain.ugc.shorts.ClipHookRepository
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.HookVariant
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
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ShortsPipelineOrchestrator — 상태 전이 게이트, 크레딧 차감/환불, 협조적 중단 검증.
 *
 * 리포지토리는 오케스트레이터의 재조회/갱신 흐름을 그대로 따라가야 하므로
 * 정적 스텁 대신 인메모리 페이크를 쓴다.
 */
class ShortsPipelineOrchestratorTest {

    // ---- 인메모리 페이크 ----

    private class InMemoryPipelineRunRepository(initial: PipelineRun) : PipelineRunRepository {
        private var current: PipelineRun = initial
        override fun save(run: PipelineRun) = run.also { current = it }
        override fun update(run: PipelineRun) = run.also { current = it }
        override fun findById(id: Long) = current.takeIf { it.id == id }
        override fun findByWorkspace(workspaceId: Long, offset: Int, limit: Int) = listOf(current)
        override fun countByWorkspace(workspaceId: Long) = 1L
        override fun delete(id: Long) = true
    }

    private class InMemoryRunStageRepository : RunStageRepository {
        val records = mutableListOf<RunStage>()
        private var nextId = 1L
        override fun save(stage: RunStage): RunStage = stage.copy(id = nextId++).also { records += it }
        override fun update(stage: RunStage): RunStage {
            records.replaceAll { if (it.id == stage.id) stage else it }
            return stage
        }
        override fun findByRunId(runId: Long) = records.filter { it.runId == runId }
        override fun findByRunIdAndStage(runId: Long, stage: PipelineStage) =
            records.lastOrNull { it.runId == runId && it.stage == stage }
        override fun deleteFrom(runId: Long, fromSortOrder: Int): Int {
            val targets = records.filter { it.runId == runId && it.stage.sortOrder >= fromSortOrder }
            records.removeAll(targets.toSet())
            return targets.size
        }
    }

    private class InMemoryShortsClipRepository(initial: List<ShortsClip> = emptyList()) : ShortsClipRepository {
        val clips = initial.toMutableList()
        private var nextId = 100L
        override fun saveAll(clips: List<ShortsClip>): List<ShortsClip> =
            clips.map { it.copy(id = nextId++) }.also { this.clips += it }
        override fun update(clip: ShortsClip): ShortsClip {
            clips.replaceAll { if (it.id == clip.id) clip else it }
            return clip
        }
        override fun findByRunId(runId: Long) = clips.filter { it.runId == runId }
        override fun findById(id: Long) = clips.find { it.id == id }
        override fun deleteByRunId(runId: Long): Int {
            val count = clips.count { it.runId == runId }
            clips.removeIf { it.runId == runId }
            return count
        }
    }

    private class InMemoryClipHookRepository(initial: List<ClipHook> = emptyList()) : ClipHookRepository {
        val hooks = initial.toMutableList()
        private var nextId = 500L
        override fun saveAll(hooks: List<ClipHook>) = hooks.map { it.copy(id = nextId++) }.also { this.hooks += it }
        override fun findByClipIds(clipIds: List<Long>) = hooks.filter { it.clipId in clipIds }
        override fun clearSelection(clipId: Long) {
            hooks.replaceAll { if (it.clipId == clipId) it.copy(selected = false) else it }
        }
        override fun markSelected(clipId: Long, variant: HookVariant, text: String): ClipHook {
            clearSelection(clipId)
            val marked = ClipHook(id = nextId++, clipId = clipId, variant = variant, text = text, selected = true)
            hooks += marked
            return marked
        }
        override fun deleteByClipIds(clipIds: List<Long>): Int {
            val count = hooks.count { it.clipId in clipIds }
            hooks.removeIf { it.clipId in clipIds }
            return count
        }
    }

    /** 호출 횟수를 세는 가짜 단계 실행기. */
    private class FakeStageExecutor(
        override val stage: PipelineStage,
        private val handler: (ShortsStageContext) -> ShortsStageOutput,
    ) : ShortsStageExecutor {
        var callCount = 0
            private set
        override fun execute(context: ShortsStageContext): ShortsStageOutput {
            callCount++
            return handler(context)
        }
    }

    // ---- 공통 픽스처 ----

    private val userId = 1L
    private val workspaceId = 10L
    private val videoId = 5L

    private val creditService = mockk<CreditService>(relaxed = true)
    private val rateLimiter = mockk<AiRateLimiter>(relaxed = true)
    private val shortsTemplateRepository = mockk<ShortsTemplateRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val userSettingsRepository = mockk<UserSettingsRepository>()

    private fun baseRun(status: PipelineRunStatus = PipelineRunStatus.PENDING) = PipelineRun(
        id = 1L, workspaceId = workspaceId, userId = userId, sourceVideoId = videoId, status = status,
    )

    private fun clip(
        id: Long,
        seq: Int,
        status: ClipStatus = ClipStatus.DRAFT,
        subtitleJson: String? = """[{"startMs":0,"endMs":1500,"text":"자막 $seq"}]""",
        scheduledAt: Instant? = null,
    ) = ShortsClip(
        id = id, runId = 1L, seq = seq,
        startMs = (seq - 1) * 15000L, endMs = seq * 15000L,
        title = "클립 $seq", caption = "캡션 $seq",
        status = status, subtitleJson = subtitleJson, scheduledAt = scheduledAt,
    )

    private fun stubCommon(templates: List<ShortsTemplate> = emptyList()) {
        every { videoRepository.findById(videoId) } returns
            Video(id = videoId, userId = userId, title = "원본 영상", fileUrl = "https://cdn.example.com/source.mp4")
        every { userSettingsRepository.findByUserId(userId) } returns null
        every { shortsTemplateRepository.findByWorkspace(workspaceId) } returns templates
        every { shortsTemplateRepository.findById(any()) } returns null
    }

    private fun orchestrator(
        runRepo: InMemoryPipelineRunRepository,
        stageRepo: InMemoryRunStageRepository,
        clipRepo: InMemoryShortsClipRepository,
        hookRepo: InMemoryClipHookRepository,
        executors: List<ShortsStageExecutor>,
    ) = ShortsPipelineOrchestrator(
        pipelineRunRepository = runRepo,
        runStageRepository = stageRepo,
        shortsClipRepository = clipRepo,
        clipHookRepository = hookRepo,
        shortsTemplateRepository = shortsTemplateRepository,
        videoRepository = videoRepository,
        creditService = creditService,
        rateLimiter = rateLimiter,
        userSettingsRepository = userSettingsRepository,
        executors = executors,
    )

    // ---- 0. REFRAME crop 영속성 (D1) ----

    @Test
    fun `REFRAME 크롭은 HOOK 게이트 재개 후에도 살아남아 클립에 기록된다`() {
        // REFRAME 결과가 메모리 컨텍스트에만 있으면 게이트에서 멈춘 뒤 재개할 때 소실된다.
        // 그러면 TEMPLATE 이 조기 반환해 클립에 crop 이 안 들어가고 세로 변환이 빠진 채 렌더된다.
        stubCommon()
        val runRepo = InMemoryPipelineRunRepository(baseRun())
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository()
        val hookRepo = InMemoryClipHookRepository()

        val cropJson = """{"x":420,"y":0,"width":1080,"height":1920}"""

        val transcribe = FakeStageExecutor(PipelineStage.TRANSCRIBE) {
            ShortsStageOutput(outputSnapshot = "{}", transcriptText = "전사", transcriptSegments = emptyList())
        }
        val reframe = FakeStageExecutor(PipelineStage.REFRAME) {
            ShortsStageOutput(outputSnapshot = "{}", cropJson = cropJson)
        }
        val segment = FakeStageExecutor(PipelineStage.SEGMENT) {
            ShortsStageOutput(
                outputSnapshot = "{}",
                clipCandidates = listOf(ClipCandidate("제목1", "캡션1", 0, 15000)),
            )
        }
        val subtitle = FakeStageExecutor(PipelineStage.SUBTITLE) { ctx ->
            ShortsStageOutput(
                outputSnapshot = "{}",
                subtitles = ctx.clips.associate { it.id to """[{"startMs":0,"endMs":1000,"text":"자막"}]""" },
            )
        }
        val hook = FakeStageExecutor(PipelineStage.HOOK) { ctx ->
            ShortsStageOutput(
                outputSnapshot = "{}",
                hooks = ctx.clips.flatMap {
                    listOf(
                        GeneratedHook(it.id, HookVariant.A, "A안 ${it.seq}"),
                        GeneratedHook(it.id, HookVariant.B, "B안 ${it.seq}"),
                    )
                },
            )
        }
        val template = FakeStageExecutor(PipelineStage.TEMPLATE) { ShortsStageOutput(outputSnapshot = "{}") }
        // 재개 시 TEMPLATE 다음 단계들도 필요하다. VALIDATE 이후 2차 게이트에서 멈춘다.
        val renderSpec = FakeStageExecutor(PipelineStage.RENDER_SPEC) { ctx ->
            ShortsStageOutput(
                outputSnapshot = "{}",
                renderSpecs = ctx.clips.associate { it.id to "{}" },
            )
        }
        val validate = FakeStageExecutor(PipelineStage.VALIDATE) { ShortsStageOutput(outputSnapshot = "{}") }
        val executors = listOf(transcribe, reframe, segment, subtitle, hook, template, renderSpec, validate)

        // 1차 실행: HOOK 게이트에서 멈춘다
        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, executors).run(1L, PipelineStage.TRANSCRIBE)
        assertEquals(PipelineRunStatus.AWAITING_HOOK_SELECTION, runRepo.findById(1L)!!.status)

        // 재개: 새 인스턴스로 TEMPLATE 부터. 메모리 컨텍스트는 남아 있지 않다
        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, executors).run(1L, PipelineStage.TEMPLATE)

        val clips = clipRepo.findByRunId(1L)
        assertTrue(clips.isNotEmpty(), "클립이 있어야 검증이 의미 있다")
        assertEquals(
            cropJson, clips.first().cropJson,
            "REFRAME 크롭이 재개 후 소실되면 세로 변환 없이 렌더된다",
        )
    }

    // ---- 1. 상태 전이: 1차 게이트 (HOOK → AWAITING_HOOK_SELECTION) ----

    @Test
    fun `HOOK 단계까지 성공하면 AWAITING_HOOK_SELECTION에서 멈추고 이후 단계는 실행하지 않는다`() {
        stubCommon()
        val runRepo = InMemoryPipelineRunRepository(baseRun())
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository()
        val hookRepo = InMemoryClipHookRepository()

        val transcribe = FakeStageExecutor(PipelineStage.TRANSCRIBE) {
            ShortsStageOutput(
                outputSnapshot = """{"text":"전사 전문","segments":[{"startMs":0,"endMs":5000,"text":"안녕"}]}""",
                transcriptText = "전사 전문",
                transcriptSegments = listOf(TranscriptSegmentMs(0, 5000, "안녕")),
            )
        }
        val reframe = FakeStageExecutor(PipelineStage.REFRAME) {
            ShortsStageOutput(outputSnapshot = "{}", cropJson = """{"x":0,"y":0,"width":1080,"height":1920}""")
        }
        val segment = FakeStageExecutor(PipelineStage.SEGMENT) {
            ShortsStageOutput(
                outputSnapshot = "{}",
                clipCandidates = listOf(
                    ClipCandidate("제목1", "캡션1", 0, 15000),
                    ClipCandidate("제목2", "캡션2", 15000, 30000),
                ),
            )
        }
        val subtitle = FakeStageExecutor(PipelineStage.SUBTITLE) { ctx ->
            ShortsStageOutput(
                outputSnapshot = "{}",
                subtitles = ctx.clips.associate { it.id to """[{"startMs":0,"endMs":1000,"text":"자막"}]""" },
            )
        }
        val hook = FakeStageExecutor(PipelineStage.HOOK) { ctx ->
            ShortsStageOutput(
                outputSnapshot = "{}",
                hooks = ctx.clips.flatMap {
                    listOf(
                        GeneratedHook(it.id, HookVariant.A, "A안 ${it.seq}"),
                        GeneratedHook(it.id, HookVariant.B, "B안 ${it.seq}"),
                    )
                },
            )
        }
        val template = FakeStageExecutor(PipelineStage.TEMPLATE) { error("HOOK 게이트 이후 단계는 실행되면 안 된다") }

        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, listOf(transcribe, reframe, segment, subtitle, hook, template))
            .run(1L, PipelineStage.TRANSCRIBE)

        // 1차 게이트에서 멈춘다
        assertEquals(PipelineRunStatus.AWAITING_HOOK_SELECTION, runRepo.findById(1L)!!.status)
        // HOOK까지의 단계만 COMPLETED로 남고 TEMPLATE 이후는 실행되지 않는다
        val completed = stageRepo.records.filter { it.status == RunStageStatus.COMPLETED }
        assertEquals(
            listOf(
                PipelineStage.TRANSCRIBE,
                PipelineStage.REFRAME,
                PipelineStage.SEGMENT,
                PipelineStage.SUBTITLE,
                PipelineStage.HOOK,
            ),
            completed.map { it.stage },
        )
        assertEquals(0, template.callCount)
        // 클립과 후킹이 저장되고 clipCount가 갱신된다
        assertEquals(2, clipRepo.clips.size)
        assertEquals(2, runRepo.findById(1L)!!.clipCount)
        assertEquals(4, hookRepo.hooks.size)
        // 전사 결과가 실행에 기록된다
        assertEquals("전사 전문", runRepo.findById(1L)!!.transcriptText)
    }

    @Test
    fun `AI 단계마다 해당 기능의 크레딧이 차감되고 단계 기록에 비용과 제공자가 남는다`() {
        stubCommon()
        val runRepo = InMemoryPipelineRunRepository(baseRun())
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository()
        val hookRepo = InMemoryClipHookRepository()

        val ok: (ShortsStageContext) -> ShortsStageOutput = { ShortsStageOutput(outputSnapshot = "{}") }
        val executors = listOf(
            FakeStageExecutor(PipelineStage.TRANSCRIBE) {
                ShortsStageOutput(outputSnapshot = """{"text":"t","segments":[]}""", transcriptText = "t")
            },
            FakeStageExecutor(PipelineStage.REFRAME, ok),
            FakeStageExecutor(PipelineStage.SEGMENT) {
                ShortsStageOutput(outputSnapshot = "{}", clipCandidates = listOf(ClipCandidate("t", "c", 0, 15000)))
            },
            FakeStageExecutor(PipelineStage.SUBTITLE, ok),
            FakeStageExecutor(PipelineStage.HOOK, ok),
        )

        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, executors).run(1L, PipelineStage.TRANSCRIBE)

        // 단계 → 기능 매핑대로 차감된다 (TRANSCRIBE=STT, REFRAME/SEGMENT/SUBTITLE/HOOK)
        verify { creditService.validateAndDeduct(userId, AiFeature.STT) }
        verify { creditService.validateAndDeduct(userId, AiFeature.SHORTS_REFRAME) }
        verify { creditService.validateAndDeduct(userId, AiFeature.SHORTS_SEGMENT) }
        verify { creditService.validateAndDeduct(userId, AiFeature.SHORTS_SUBTITLE) }
        verify { creditService.validateAndDeduct(userId, AiFeature.SHORTS_HOOK) }
        verify(exactly = 5) { creditService.validateAndDeduct(userId, any<AiFeature>()) }
        // 단계 기록에 기능별 비용과 AI 제공자가 남는다 (UserSettings 없으면 QWEN)
        val costByStage = stageRepo.records.associate { it.stage to it.creditCost }
        assertEquals(AiFeature.STT.creditCost, costByStage[PipelineStage.TRANSCRIBE])
        assertEquals(AiFeature.SHORTS_HOOK.creditCost, costByStage[PipelineStage.HOOK])
        assertEquals("QWEN", stageRepo.records.first { it.stage == PipelineStage.HOOK }.aiProvider)
    }

    // ---- 1. 상태 전이: 2차 게이트 (VALIDATE → AWAITING_SCHEDULE) ----

    @Test
    fun `TEMPLATE부터 재개하면 VALIDATE까지 돌고 AWAITING_SCHEDULE에서 멈춘다`() {
        val defaultTemplate = ShortsTemplate(
            id = 7, workspaceId = workspaceId, name = "기본",
            hookPosition = "TOP", captionFontFamily = "Pretendard", isDefault = true, createdBy = userId,
        )
        stubCommon(templates = listOf(defaultTemplate))
        val runRepo = InMemoryPipelineRunRepository(
            baseRun(PipelineRunStatus.AWAITING_HOOK_SELECTION).copy(clipCount = 2, transcriptText = "전사 전문"),
        )
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository(
            listOf(clip(11, 1, ClipStatus.HOOK_SELECTED), clip(12, 2, ClipStatus.HOOK_SELECTED)),
        )
        val hookRepo = InMemoryClipHookRepository(
            listOf(
                ClipHook(id = 21, clipId = 11, variant = HookVariant.B, text = "선택된 후킹 1", selected = true),
                ClipHook(id = 22, clipId = 12, variant = HookVariant.A, text = "선택된 후킹 2", selected = true),
            ),
        )

        val builder = ShortsRenderSpecBuilder()
        val template = FakeStageExecutor(PipelineStage.TEMPLATE) { ShortsStageOutput(outputSnapshot = "{}") }
        val renderSpec = RenderSpecStageExecutor(builder) // 실제 실행기로 스펙 생성까지 검증한다
        val validate = FakeStageExecutor(PipelineStage.VALIDATE) { ShortsStageOutput(outputSnapshot = "{}") }
        val schedule = FakeStageExecutor(PipelineStage.SCHEDULE) { error("예약 파라미터 없이 SCHEDULE이 실행되면 안 된다") }

        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, listOf(template, renderSpec, validate, schedule))
            .run(1L, PipelineStage.TEMPLATE)

        // 2차 게이트에서 멈춘다
        assertEquals(PipelineRunStatus.AWAITING_SCHEDULE, runRepo.findById(1L)!!.status)
        assertEquals(0, schedule.callCount)
        val completed = stageRepo.records.filter { it.status == RunStageStatus.COMPLETED }
        assertEquals(
            listOf(PipelineStage.TEMPLATE, PipelineStage.RENDER_SPEC, PipelineStage.VALIDATE),
            completed.map { it.stage },
        )
        // 클립에 렌더 스펙이 기록되고 RENDER_READY로 올라간다
        val clip1 = clipRepo.findById(11L)!!
        assertEquals(ClipStatus.RENDER_READY, clip1.status)
        val spec = builder.parseSpec(clip1.renderSpec!!)
        assertEquals("선택된 후킹 1", spec.hookText)
        assertEquals(7, spec.templateId)
        // RENDER_SPEC은 AI 단계가 아니라 차감이 없다
        verify { creditService.validateAndDeduct(userId, AiFeature.SHORTS_TEMPLATE) }
        verify { creditService.validateAndDeduct(userId, AiFeature.SHORTS_VALIDATE) }
        verify(exactly = 2) { creditService.validateAndDeduct(userId, any<AiFeature>()) }
        assertEquals(0, stageRepo.records.first { it.stage == PipelineStage.RENDER_SPEC }.creditCost)
        assertNull(stageRepo.records.first { it.stage == PipelineStage.RENDER_SPEC }.aiProvider)
    }

    // ---- 1. 상태 전이: 종료 (SCHEDULE → COMPLETED) ----

    @Test
    fun `예약 파라미터와 함께 SCHEDULE을 실행하면 클립이 예약되고 COMPLETED로 끝난다`() {
        stubCommon()
        val runRepo = InMemoryPipelineRunRepository(baseRun(PipelineRunStatus.AWAITING_SCHEDULE).copy(clipCount = 3))
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository(
            listOf(
                clip(11, 1, ClipStatus.RENDER_READY),
                clip(12, 2, ClipStatus.DISCARDED),
                clip(13, 3, ClipStatus.RENDER_READY),
            ),
        )
        val hookRepo = InMemoryClipHookRepository()

        val startAt = Instant.parse("2026-03-01T09:00:00Z")
        // 여기서는 상태 전이와 예약 시각 계산만 본다. 플랫폼을 비우면 게시 위임은 일어나지 않는다.
        // 게시 위임 자체는 ScheduleStageExecutorTest 가 검증한다.
        val scheduleExecutor = ScheduleStageExecutor(mockk(), mockk(relaxed = true))
        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, listOf(scheduleExecutor))
            .run(1L, PipelineStage.SCHEDULE, ScheduleParams(startAt, 6, emptyList()))

        assertEquals(PipelineRunStatus.COMPLETED, runRepo.findById(1L)!!.status)
        // DISCARDED는 빼고 seq 순으로 6시간 간격
        val clip1 = clipRepo.findById(11L)!!
        val clip3 = clipRepo.findById(13L)!!
        assertEquals(ClipStatus.SCHEDULED, clip1.status)
        assertEquals(startAt, clip1.scheduledAt)
        assertEquals(ClipStatus.SCHEDULED, clip3.status)
        assertEquals(startAt.plusSeconds(6 * 3600L), clip3.scheduledAt)
        // 폐기된 클립은 그대로다
        val clip2 = clipRepo.findById(12L)!!
        assertEquals(ClipStatus.DISCARDED, clip2.status)
        assertNull(clip2.scheduledAt)
        // SCHEDULE은 크레딧 차감이 없다
        verify(exactly = 0) { creditService.validateAndDeduct(any(), any<AiFeature>()) }
        assertEquals(0, stageRepo.records.first { it.stage == PipelineStage.SCHEDULE }.creditCost)
    }

    // ---- 6. 크레딧: 단계 실패 시 해당 단계분만 환불 ----

    @Test
    fun `SEGMENT 단계가 실패하면 그 단계 크레딧만 환불하고 FAILED로 끝낸다`() {
        stubCommon()
        val runRepo = InMemoryPipelineRunRepository(baseRun())
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository()
        val hookRepo = InMemoryClipHookRepository()

        val transcribe = FakeStageExecutor(PipelineStage.TRANSCRIBE) {
            ShortsStageOutput(outputSnapshot = """{"text":"t","segments":[]}""", transcriptText = "t")
        }
        val reframe = FakeStageExecutor(PipelineStage.REFRAME) { ShortsStageOutput(outputSnapshot = "{}") }
        val segment = FakeStageExecutor(PipelineStage.SEGMENT) { throw RuntimeException("AI 응답 파싱 실패") }
        val subtitle = FakeStageExecutor(PipelineStage.SUBTITLE) { ShortsStageOutput(outputSnapshot = "{}") }
        val hook = FakeStageExecutor(PipelineStage.HOOK) { ShortsStageOutput(outputSnapshot = "{}") }

        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, listOf(transcribe, reframe, segment, subtitle, hook))
            .run(1L, PipelineStage.TRANSCRIBE)

        // 실행은 FAILED, 오류 메시지에 실패한 단계가 드러난다
        val failed = runRepo.findById(1L)!!
        assertEquals(PipelineRunStatus.FAILED, failed.status)
        assertTrue(failed.errorMessage!!.contains("맥락 컷 단계 실패"), failed.errorMessage!!)
        // 이전 단계는 COMPLETED, 실패 단계는 FAILED + 오류 메시지
        assertEquals(RunStageStatus.COMPLETED, stageRepo.findByRunIdAndStage(1L, PipelineStage.TRANSCRIBE)!!.status)
        assertEquals(RunStageStatus.COMPLETED, stageRepo.findByRunIdAndStage(1L, PipelineStage.REFRAME)!!.status)
        val failedStage = stageRepo.findByRunIdAndStage(1L, PipelineStage.SEGMENT)!!
        assertEquals(RunStageStatus.FAILED, failedStage.status)
        assertEquals("AI 응답 파싱 실패", failedStage.errorMessage)
        assertNotNull(failedStage.completedAt)
        // 이후 단계는 실행되지 않는다
        assertEquals(0, subtitle.callCount)
        assertEquals(0, hook.callCount)
        // 차감은 실패 단계까지 일어났고, 환불은 실패 단계분(SEGMENT=8)만 한 번
        verify { creditService.validateAndDeduct(userId, AiFeature.STT) }
        verify { creditService.validateAndDeduct(userId, AiFeature.SHORTS_REFRAME) }
        verify { creditService.validateAndDeduct(userId, AiFeature.SHORTS_SEGMENT) }
        verify(exactly = 1) {
            creditService.refundCredit(userId, AiFeature.SHORTS_SEGMENT.creditCost, "SHORTS_SEGMENT")
        }
        verify(exactly = 1) { creditService.refundCredit(any(), any(), any()) }
    }

    @Test
    fun `크레딧 차감 전에 실패하면 환불하지 않는다`() {
        stubCommon()
        val runRepo = InMemoryPipelineRunRepository(baseRun())
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository()
        val hookRepo = InMemoryClipHookRepository()

        // 차감 자체가 실패 (크레딧 부족 등)
        every { creditService.validateAndDeduct(userId, AiFeature.STT) } throws RuntimeException("크레딧 부족")
        val transcribe = FakeStageExecutor(PipelineStage.TRANSCRIBE) { ShortsStageOutput(outputSnapshot = "{}") }

        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, listOf(transcribe)).run(1L, PipelineStage.TRANSCRIBE)

        assertEquals(PipelineRunStatus.FAILED, runRepo.findById(1L)!!.status)
        assertEquals(0, transcribe.callCount)
        verify(exactly = 0) { creditService.refundCredit(any(), any(), any()) }
    }

    // ---- 협조적 중단 ----

    @Test
    fun `CANCELLED 상태의 실행은 아무 단계도 돌리지 않는다`() {
        stubCommon()
        val runRepo = InMemoryPipelineRunRepository(baseRun(PipelineRunStatus.CANCELLED))
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository()
        val hookRepo = InMemoryClipHookRepository()

        val transcribe = FakeStageExecutor(PipelineStage.TRANSCRIBE) { ShortsStageOutput(outputSnapshot = "{}") }

        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, listOf(transcribe)).run(1L, PipelineStage.TRANSCRIBE)

        assertEquals(PipelineRunStatus.CANCELLED, runRepo.findById(1L)!!.status)
        assertEquals(0, transcribe.callCount)
        assertTrue(stageRepo.records.isEmpty())
        verify(exactly = 0) { creditService.validateAndDeduct(any(), any<AiFeature>()) }
    }
}
