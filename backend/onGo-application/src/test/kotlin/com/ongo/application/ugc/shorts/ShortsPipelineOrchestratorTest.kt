package com.ongo.application.ugc.shorts

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.credit.CreditService
import com.ongo.application.ugc.shorts.stage.ClipCandidate
import com.ongo.application.ugc.shorts.stage.GeneratedHook
import com.ongo.application.ugc.shorts.stage.RenderSpecStageExecutor
import com.ongo.application.ugc.shorts.stage.ScheduleParams
import com.ongo.application.ugc.shorts.stage.ScheduleOutcome
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
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

        /** 이 페이크는 실행 하나만 들고 있다. 조건만 실제 질의와 같게 맞춘다. */
        override fun findFailedWithUnsettledStages(limit: Int) =
            listOf(current).filter { it.status == PipelineRunStatus.FAILED }.take(limit)

        /**
         * 정상 이벤트는 발행 직전에 반드시 `PENDING` 으로 무장된다. 조건을 넓히면 게이트나
         * 완료 뒤에 도착한 중복 이벤트가 통과해 이중 청구가 되살아난다.
         */
        /**
         * 실제 구현의 `WHERE status='RUNNING' AND version = ?` 과 같은 계약.
         * version 조건을 빼면 살아 있는 작업의 진행을 놓쳐 복구가 그것을 덮어쓴다.
         */
        override fun failStale(id: Long, expectedVersion: Long, reason: String): Boolean {
            if (current.id != id) return false
            if (current.status != PipelineRunStatus.RUNNING) return false
            if (current.version != expectedVersion) return false
            current = current.copy(
                status = PipelineRunStatus.FAILED,
                errorMessage = reason,
                version = expectedVersion + 1,
            )
            return true
        }

        override fun claimRunning(id: Long): Boolean {
            if (current.id != id) return false
            // 실제 구현의 `WHERE status = 'PENDING'` 과 같은 계약.
            if (current.status != PipelineRunStatus.PENDING) return false
            // 확보도 진척이다 — version·updatedAt 을 함께 옮겨야 복구기의 낡은 관측이 빗나간다.
            current = current.copy(
                status = PipelineRunStatus.RUNNING,
                errorMessage = null,
                version = current.version + 1,
                updatedAt = Instant.now(),
            )
            return true
        }

        /** 실제 구현의 `WHERE started_at IS NULL` 과 같은 계약: 비어 있을 때만 쓴다. */
        override fun markStartedIfAbsent(id: Long, startedAt: Instant): Boolean {
            if (current.id != id || current.startedAt != null) return false
            current = current.copy(startedAt = startedAt)
            return true
        }

        override fun markDeliveredIfAbsent(id: Long, deliveredAt: Instant): Boolean {
            if (current.id != id || current.deliveredAt != null) return false
            current = current.copy(deliveredAt = deliveredAt)
            return true
        }
        override fun findByStatus(status: PipelineRunStatus, limit: Int) =
            listOf(current).filter { it.status == status }.take(limit)
        override fun findByWorkspace(workspaceId: Long, offset: Int, limit: Int) = listOf(current)
        override fun countByWorkspace(workspaceId: Long) = 1L
        override fun delete(id: Long) = true
    }

    private class InMemoryRunStageRepository : RunStageRepository {
        val records = mutableListOf<RunStage>()
        private var nextId = 1L
        override fun save(stage: RunStage): RunStage = stage.copy(id = nextId++).also { records += it }
        /** 오케스트레이터가 단계를 **직접 닫았는지** 관측하기 위한 기록. */
        val updatedStatuses = mutableListOf<Pair<Long, RunStageStatus>>()

        override fun update(stage: RunStage): RunStage {
            updatedStatuses += stage.id to stage.status
            records.replaceAll { if (it.id == stage.id) stage else it }
            return stage
        }
        override fun findByRunId(runId: Long) = records.filter { it.runId == runId }
        override fun findByRunIdAndStage(runId: Long, stage: PipelineStage) =
            records.lastOrNull { it.runId == runId && it.stage == stage }
        /** 실제 구현과 같은 조건: RUNNING · 미정산 · 청구액 있음. 완료 단계는 제외된다. */
        override fun findUnsettled(runId: Long, fromSortOrder: Int): List<RunStage> =
            records.filter {
                it.runId == runId &&
                    it.stage.sortOrder >= fromSortOrder &&
                    it.status == RunStageStatus.RUNNING &&
                    it.refundedCredits == 0 &&
                    it.creditCost > 0
            }

        /** 실제 구현의 조건부 갱신과 같은 계약. 조건을 빼면 이중 환불이 열린다. */
        override fun settleRefund(stageId: Long, refundedCredits: Int, reason: String): Boolean {
            val idx = records.indexOfFirst { it.id == stageId }
            if (idx < 0) return false
            val current = records[idx]
            if (current.status != RunStageStatus.RUNNING || current.refundedCredits != 0) return false
            records[idx] = current.copy(
                status = RunStageStatus.FAILED,
                refundedCredits = refundedCredits,
                errorMessage = reason,
                // 실제 구현이 COMPLETED_AT 을 채운다. 빠뜨리면 계약이 갈린다.
                completedAt = Instant.now(),
            )
            return true
        }

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

    /**
     * @param sourceDurationMs 실행에 고정된 원본 길이. 기본값은 이 필드 도입 이전 실행과
     *   같은 `null` 이라, 이 값을 지정하지 않은 기존 테스트는 종전 정액을 그대로 본다.
     */
    private fun baseRun(
        status: PipelineRunStatus = PipelineRunStatus.PENDING,
        sourceDurationMs: Long? = null,
    ) = PipelineRun(
        id = 1L, workspaceId = workspaceId, userId = userId, sourceVideoId = videoId, status = status,
        sourceDurationMs = sourceDurationMs,
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

    /**
     * TRANSCRIBE~HOOK 실행기 한 벌. 오케스트레이터는 fromStage 이후 모든 단계를 돌리므로
     * 중간 단계 실행기가 없으면 "단계 실행기가 없습니다"로 죽는다. 후킹 게이트에서 멈춘다.
     */
    private fun hookGateExecutors(): List<ShortsStageExecutor> {
        val ok: (ShortsStageContext) -> ShortsStageOutput = { ShortsStageOutput(outputSnapshot = "{}") }
        return listOf(
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
    }

    /**
     * 단계마다 **서로 다른 영수증**을 돌려준다. 그래야 "실패한 그 단계의 차감"이
     * 환불됐는지 확인할 수 있다. 하나만 쓰면 아무 단계나 환불해도 통과한다.
     */
    private val allocations = mutableMapOf<String, com.ongo.application.credit.CreditAllocation>()

    /**
     * 실제로 돈이 나간 영수증을 만든다.
     *
     * 예전에는 `empty`(총액 0)였다. 그러면 저장할 분해가 없어 **크래시 후 환불 경로가 전혀
     * 실행되지 않는다** — 테스트가 통과해도 실제 계약을 재지 못한다.
     */
    private fun allocationFor(featureName: String, amount: Int) =
        allocations.getOrPut(featureName) {
            com.ongo.application.credit.CreditAllocation.restored(userId, featureName, amount, emptyMap())
        }

    private fun stubAllocations() {
        every { creditService.validateAndDeduct(userId, any<Int>(), any<String>()) } answers {
            allocationFor(thirdArg(), secondArg())
        }
    }

    private fun stubCommon(templates: List<ShortsTemplate> = emptyList()) {
        stubAllocations()
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
        // 실제 구현을 쓴다 — 목으로 바꾸면 차감·정산 배선이 검증되지 않는다.
        // 목 creditService 를 그대로 넘기므로 기존 차감/환불 단정은 그대로 동작한다.
        stageCreditService = ShortsStageCreditService(creditService, stageRepo),
        rateLimiter = rateLimiter,
        userSettingsRepository = userSettingsRepository,
        executors = executors,
    )

    // ---- 중복 실행 청구 ----

    /**
     * **같은 실행이 두 번 돌면 모든 AI 단계가 두 번 청구된다.**
     *
     * 실행은 `@Async @TransactionalEventListener(AFTER_COMMIT)` 로 시작되고, 그 이벤트를
     * 내는 곳이 다섯 군데다(생성·단계 재실행·후킹 선택·예약 확정·자동 예약 워커).
     * 재실행 API 의 `status == RUNNING` 가드는 **잠금 없이 읽고 판단**하므로, 버튼을 두 번
     * 누르거나 커밋과 비동기 리스너 사이의 틈에 두 번째 요청이 들어오면 두 이벤트가 모두
     * 통과한다.
     *
     * 그 뒤 [ShortsPipelineOrchestrator.run] 은 `CANCELLED` 만 확인하고 조건 없이 RUNNING
     * 으로 쓰기 때문에 두 실행이 나란히 진행된다. TRANSCRIBE 는 원본 길이에 비례해
     * 청구되므로 긴 영상일수록 이중 차감액이 커진다.
     *
     * 여기서는 첫 실행이 TRANSCRIBE 를 도는 **도중에** 두 번째 이벤트가 도착한 상황을
     * 재진입으로 재현한다 — 그 시점의 상태는 실제 동시 실행과 똑같이 RUNNING 이다.
     */
    @Test
    fun `실행 중에 도착한 두 번째 이벤트는 같은 단계를 다시 청구하지 않는다`() {
        val runRepo = InMemoryPipelineRunRepository(baseRun())
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository()
        val hookRepo = InMemoryClipHookRepository()
        lateinit var subject: ShortsPipelineOrchestrator
        var reentered = false

        val executors = hookGateExecutors().map { executor ->
            if (executor.stage != PipelineStage.TRANSCRIBE) executor
            else FakeStageExecutor(PipelineStage.TRANSCRIBE) {
                // 첫 실행이 아직 이 단계 안에 있는 동안 두 번째 이벤트가 도착한다.
                if (!reentered) {
                    reentered = true
                    subject.run(1L, PipelineStage.TRANSCRIBE)
                }
                ShortsStageOutput(outputSnapshot = """{"text":"t","segments":[]}""", transcriptText = "t")
            }
        }
        subject = orchestrator(runRepo, stageRepo, clipRepo, hookRepo, executors)
        stubCommon()

        subject.run(1L, PipelineStage.TRANSCRIBE)

        assertTrue(reentered, "두 번째 이벤트가 재현되지 않았다")
        // 사용자는 한 번만 요청했다. 청구도 한 번이어야 한다.
        verify(exactly = 1) {
            creditService.validateAndDeduct(userId, any(), AiFeature.STT.name)
        }
    }

    /**
     * **첫 실행이 끝난 뒤 도착한 중복 이벤트도 다시 청구하면 안 된다.**
     *
     * 재실행 API 의 상태 가드가 잠금 없이 판단하므로 버튼을 두 번 누르면 이벤트가 두 개
     * 발행된다. 두 이벤트가 겹쳐 도착하면 두 번째는 `RUNNING` 을 보고 거절되지만,
     * **첫 실행이 후킹 게이트에서 멈춘 뒤**에 도착하면 그때 상태는 `AWAITING_HOOK_SELECTION`
     * 이다. 확보 조건이 "RUNNING·CANCELLED 만 제외" 라면 그 상태는 통과하고, 파이프라인이
     * 처음부터 다시 돌아 모든 AI 단계가 두 번 청구된다.
     *
     * 정상 이벤트는 발행 직전에 **반드시 PENDING 으로 전환된 뒤** 나온다(생성·단계 재실행·
     * 후킹 확정·예약 확정·자동 예약 워커 다섯 곳 모두). 그러므로 PENDING 이 아닌 상태에서
     * 들어온 실행 요청은 중복이다.
     */
    @Test
    fun `게이트에서 멈춘 뒤 도착한 중복 이벤트는 다시 청구하지 않는다`() {
        val runRepo = InMemoryPipelineRunRepository(baseRun())
        val stageRepo = InMemoryRunStageRepository()
        val subject = orchestrator(
            runRepo, stageRepo, InMemoryShortsClipRepository(), InMemoryClipHookRepository(),
            hookGateExecutors(),
        )
        stubCommon()

        subject.run(1L, PipelineStage.TRANSCRIBE)
        // 후킹 게이트에서 멈춘 상태다 — 사용자의 선택을 기다린다.
        assertEquals(PipelineRunStatus.AWAITING_HOOK_SELECTION, runRepo.findById(1L)!!.status)

        // 같은 요청에서 발행된 두 번째 이벤트가 뒤늦게 도착한다.
        subject.run(1L, PipelineStage.TRANSCRIBE)

        verify(exactly = 1) {
            creditService.validateAndDeduct(userId, any(), AiFeature.STT.name)
        }
    }

    /**
     * 완료된 실행에 뒤늦은 이벤트가 닿아도 마찬가지다. 결과물은 이미 납품됐고,
     * 다시 도는 것은 순수한 손실이다.
     */
    @Test
    fun `완료된 실행에 도착한 중복 이벤트는 다시 청구하지 않는다`() {
        val runRepo = InMemoryPipelineRunRepository(baseRun(status = PipelineRunStatus.COMPLETED))
        val subject = orchestrator(
            runRepo, InMemoryRunStageRepository(), InMemoryShortsClipRepository(),
            InMemoryClipHookRepository(), hookGateExecutors(),
        )
        stubCommon()

        subject.run(1L, PipelineStage.TRANSCRIBE)

        verify(exactly = 0) { creditService.validateAndDeduct(userId, any(), any<String>()) }
        assertEquals(PipelineRunStatus.COMPLETED, runRepo.findById(1L)!!.status)
    }

    /**
     * **크래시로 `RUNNING` 에 고착된 실행은 스스로 풀리지 않는다.**
     *
     * 오케스트레이터는 `@Transactional` 이 아니다. 단계마다 `validateAndDeduct` 가 자기
     * 트랜잭션으로 **즉시 커밋**하므로, 프로세스가 단계 도중 죽으면 그 차감은 이미 확정돼 있고
     * 실패 경로의 환불(`refundAllocation`)은 실행되지 않는다. 사용자는 돈을 냈고 결과는 없다.
     *
     * 그 상태에서 남은 길이 없다.
     *  - 새 이벤트: 확보 조건이 `PENDING` 이라 `RUNNING` 은 통과하지 못한다(아래 검증).
     *  - 재실행 API: `rerunStage` 가 `RUNNING` 을 명시적으로 거절한다.
     *  - 남는 것은 실행을 **삭제하고 처음부터 다시 결제하는 것**뿐이다.
     *
     * 이 테스트는 그 막다른 길을 고정한다. 복구 경로가 생기면 그 경로가 이 상태를 풀어야 한다.
     */
    @Test
    fun `크래시로 RUNNING 에 고착된 실행은 새 이벤트로 진행되지 않는다`() {
        val runRepo = InMemoryPipelineRunRepository(baseRun(status = PipelineRunStatus.RUNNING))
        val subject = orchestrator(
            runRepo, InMemoryRunStageRepository(), InMemoryShortsClipRepository(),
            InMemoryClipHookRepository(), hookGateExecutors(),
        )
        stubCommon()

        subject.run(1L, PipelineStage.TRANSCRIBE)

        // 재실행되지 않는 것은 옳다 — 살아 있는 작업과 겹치면 이중 청구가 된다.
        verify(exactly = 0) { creditService.validateAndDeduct(userId, any(), any<String>()) }
        // 그러나 상태도 그대로라, 사용자가 스스로 벗어날 방법이 없다.
        assertEquals(PipelineRunStatus.RUNNING, runRepo.findById(1L)!!.status)
    }

    /**
     * **복구된 실행은 사용자가 누를 때까지 다시 돌지 않는다.**
     *
     * 고착 복구는 `FAILED` 로만 되돌린다. 그 상태로 이벤트가 들어와도 확보 조건(`PENDING`)을
     * 만족하지 않으므로 자동 재실행도 자동 청구도 없다. 다시 돌릴지는 재실행 API 를 통해
     * 사용자가 정하고, 그때 비로소 `PENDING` 으로 무장된다.
     */
    @Test
    fun `고착 복구 후에도 이벤트만으로는 다시 청구되지 않는다`() {
        val runRepo = InMemoryPipelineRunRepository(baseRun(status = PipelineRunStatus.RUNNING))
        val subject = orchestrator(
            runRepo, InMemoryRunStageRepository(), InMemoryShortsClipRepository(),
            InMemoryClipHookRepository(), hookGateExecutors(),
        )
        stubCommon()

        // 복구기가 하는 일과 같다 — 관측한 version 으로 FAILED 로만 되돌린다.
        val observed = runRepo.findById(1L)!!
        assertTrue(runRepo.failStale(1L, observed.version, "서버가 중단되어 실행이 멈췄습니다"))
        assertEquals(PipelineRunStatus.FAILED, runRepo.findById(1L)!!.status)

        subject.run(1L, PipelineStage.TRANSCRIBE)

        verify(exactly = 0) { creditService.validateAndDeduct(userId, any(), any<String>()) }
        assertEquals(PipelineRunStatus.FAILED, runRepo.findById(1L)!!.status)
    }

    /**
     * **확보는 관측 시점을 갱신해야 한다.**
     *
     * 고착 복구기는 `RUNNING` 목록을 읽어 관측한 `version` 으로 CAS 를 건다. 그런데 확보가
     * `version` 을 올리지 않으면, **확보 직후 `activeRunIds` 에 등록되기 전 창**에서 복구기가
     * 확보 이전에 읽은 값 그대로 CAS 에 성공한다. 방금 시작한 실행이 `FAILED` 로 바뀌는데도
     * 오케스트레이터는 단계를 계속 돌리고, 그 사이 사용자가 재실행을 누르면 **같은 작업이
     * 두 번 청구된다.**
     *
     * 확보가 `version` 을 올리면 그 CAS 는 반드시 빗나간다 — 복구기의 관측이 낡았다는 사실이
     * 조건 자체로 드러난다. 레지스트리 등록 타이밍에 기대지 않는 방어다.
     */
    @Test
    fun `확보 직후에는 확보 전 version 으로 복구되지 않는다`() {
        val runRepo = InMemoryPipelineRunRepository(baseRun().copy(version = 7L))
        val observedBeforeClaim = runRepo.findById(1L)!!.version

        assertTrue(runRepo.claimRunning(1L), "확보에 실패하면 이 테스트는 의미가 없다")

        // 복구기가 확보 직전에 읽은 version 으로 CAS 를 시도한다.
        assertFalse(
            runRepo.failStale(1L, observedBeforeClaim, "중단됨"),
            "확보 직후인 실행이 낡은 version 으로 FAILED 가 됐다",
        )
        assertEquals(PipelineRunStatus.RUNNING, runRepo.findById(1L)!!.status)
    }

    /* ── 재실행 청구 정확성 ─────────────────────────────────────────── */

    /**
     * **(2) 단계 완료 후 재실행 — 이미 완료·청구된 단계는 다시 청구하지 않는다.**
     *
     * 오케스트레이터는 `fromStage.sortOrder` 이상만 돌린다. 재실행 API 는 그 단계부터의 기록만
     * 지우므로, 앞선 단계는 결과도 청구도 그대로 남는다. 이 필터가 느슨해지면 뒤 단계 하나를
     * 다시 돌리려던 사용자가 파이프라인 전체를 다시 결제하게 된다.
     */
    @Test
    fun `뒤 단계부터 재실행하면 앞선 완료 단계는 다시 청구하지 않는다`() {
        val runRepo = InMemoryPipelineRunRepository(baseRun())
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository()
        val hookRepo = InMemoryClipHookRepository()
        val subject = { orchestrator(runRepo, stageRepo, clipRepo, hookRepo, hookGateExecutors()) }
        stubCommon()

        subject().run(1L, PipelineStage.TRANSCRIBE)
        assertEquals(PipelineRunStatus.AWAITING_HOOK_SELECTION, runRepo.findById(1L)!!.status)

        // 후킹 확정이 PENDING 으로 무장한 뒤 HOOK 부터 다시 돌린다.
        runRepo.update(runRepo.findById(1L)!!.copy(status = PipelineRunStatus.PENDING))
        subject().run(1L, PipelineStage.HOOK)

        // 앞선 단계는 한 번만 청구된다.
        verify(exactly = 1) { creditService.validateAndDeduct(userId, any(), AiFeature.STT.name) }
        verify(exactly = 1) { creditService.validateAndDeduct(userId, any(), AiFeature.SHORTS_REFRAME.name) }
        verify(exactly = 1) { creditService.validateAndDeduct(userId, any(), AiFeature.SHORTS_SEGMENT.name) }
        // 재실행 대상 단계만 두 번째 청구가 있다.
        verify(exactly = 2) { creditService.validateAndDeduct(userId, any(), AiFeature.SHORTS_HOOK.name) }
    }

    /**
     * **(4) 고착 복구 뒤 재시도 — 중단된 단계부터만 청구된다.**
     *
     * 크래시로 `RUNNING` 에 고착된 실행을 복구기가 `FAILED` 로 되돌린 뒤, 사용자가 중단된
     * 단계부터 다시 시도하는 전체 여정이다. 완료된 앞 단계가 다시 청구되면 사용자는 이미
     * 받은 결과에 두 번 결제하게 된다.
     */
    @Test
    fun `고착 복구 후 중단된 단계부터 재시도하면 앞 단계는 다시 청구되지 않는다`() {
        val runRepo = InMemoryPipelineRunRepository(baseRun())
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository()
        val hookRepo = InMemoryClipHookRepository()
        stubCommon()

        // 1) TRANSCRIBE~SEGMENT 까지 진행하다 SEGMENT 에서 프로세스가 죽었다고 본다.
        val crashing = hookGateExecutors().map {
            if (it.stage != PipelineStage.SUBTITLE) it
            else FakeStageExecutor(PipelineStage.SUBTITLE) { error("프로세스 중단") }
        }
        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, crashing).run(1L, PipelineStage.TRANSCRIBE)

        // 2) 고착 복구기가 하는 일 — FAILED 로만 되돌린다.
        runRepo.update(runRepo.findById(1L)!!.copy(status = PipelineRunStatus.RUNNING))
        val observed = runRepo.findById(1L)!!
        assertTrue(runRepo.failStale(1L, observed.version, "서버가 중단되어 실행이 멈췄습니다"))

        // 3) 사용자가 중단된 단계부터 재시도한다(재실행 API 가 PENDING 으로 무장).
        stageRepo.deleteFrom(1L, PipelineStage.SUBTITLE.sortOrder)
        runRepo.update(runRepo.findById(1L)!!.copy(status = PipelineRunStatus.PENDING))
        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, hookGateExecutors())
            .run(1L, PipelineStage.SUBTITLE)

        // 완료된 앞 단계는 정확히 한 번만 청구됐다.
        verify(exactly = 1) { creditService.validateAndDeduct(userId, any(), AiFeature.STT.name) }
        verify(exactly = 1) { creditService.validateAndDeduct(userId, any(), AiFeature.SHORTS_REFRAME.name) }
        verify(exactly = 1) { creditService.validateAndDeduct(userId, any(), AiFeature.SHORTS_SEGMENT.name) }
        // 중단된 단계는 실패분이 환불되고 재시도에서 다시 청구된다.
        verify(exactly = 2) { creditService.validateAndDeduct(userId, any(), AiFeature.SHORTS_SUBTITLE.name) }
        verify(exactly = 1) { creditService.refundAllocation(any()) }
    }

    /* ── 크래시 후 새 컨텍스트 정산 ──────────────────────────────────── */

    /**
     * **(핵심) 프로세스가 죽어도 다른 컨텍스트가 정확히 한 번 환불한다.**
     *
     * 차감·단계 행·분해가 한 커밋이므로, 인메모리 영수증을 잃은 뒤에도 DB 에 남은 분해로
     * 되돌릴 수 있다. 예전에는 영수증이 지역 변수뿐이라 이 상황에서 크레딧이 영영 사라졌다.
     */
    @Test
    fun `크래시로 잃은 영수증을 새 컨텍스트가 저장된 분해로 환불한다`() {
        val stageRepo = InMemoryRunStageRepository()
        stubCommon()
        /*
         * 공통 스텁의 영수증은 총액 0 이라 저장할 분해가 없다. 여기서는 **실제로 돈이 나간**
         * 차감을 재현해야 하므로 무료 2 + 구매 패키지 3 짜리 영수증을 준다.
         */
        every { creditService.validateAndDeduct(userId, 5, AiFeature.STT.name) } returns
            com.ongo.application.credit.CreditAllocation.restored(userId, AiFeature.STT.name, 2, mapOf(11L to 3))

        // 1) TRANSCRIBE 차감까지 커밋된 직후 프로세스가 죽었다고 본다.
        val charging = ShortsStageCreditService(creditService, stageRepo)
        charging.chargeAndOpenStage(1L, userId, PipelineStage.TRANSCRIBE, AiFeature.STT.name, 5)

        val open = stageRepo.findByRunIdAndStage(1L, PipelineStage.TRANSCRIBE)!!
        assertEquals(RunStageStatus.RUNNING, open.status)
        assertNotNull(open.creditAllocation, "분해가 없으면 새 컨텍스트가 환불할 수 없다")

        // 2) 새 프로세스(새 서비스 인스턴스)가 정산한다 — 인메모리 영수증은 없다.
        val recovering = ShortsStageCreditService(creditService, stageRepo)
        val unsettled = stageRepo.findUnsettled(1L)
        assertEquals(1, unsettled.size)
        assertTrue(recovering.settleStage(userId, unsettled.single(), "서버 중단"))

        verify(exactly = 1) { creditService.refundAllocation(any()) }

        // 3) 두 번째 정산은 아무 일도 하지 않는다 — 표식이 DB 에 있다.
        assertTrue(stageRepo.findUnsettled(1L).isEmpty(), "정산된 단계가 다시 미정산으로 잡힌다")
        assertFalse(recovering.settleStage(userId, unsettled.single(), "서버 중단"))
        verify(exactly = 1) { creditService.refundAllocation(any()) }
    }

    /**
     * **정상 완료된 단계는 어떤 경로에서도 환불되지 않는다.**
     *
     * 그 단계는 실제로 일한 대가로 정당하게 청구된 것이다. 환불하면 우리가 받은 적 없는
     * 돈을 돌려주는 것이 된다.
     */
    @Test
    fun `정상 완료된 단계는 미정산으로 잡히지 않는다`() {
        val runRepo = InMemoryPipelineRunRepository(baseRun())
        val stageRepo = InMemoryRunStageRepository()
        stubCommon()

        orchestrator(runRepo, stageRepo, InMemoryShortsClipRepository(), InMemoryClipHookRepository(), hookGateExecutors())
            .run(1L, PipelineStage.TRANSCRIBE)
        assertEquals(PipelineRunStatus.AWAITING_HOOK_SELECTION, runRepo.findById(1L)!!.status)

        // 게이트까지의 단계는 모두 COMPLETED 다.
        assertTrue(
            stageRepo.records.filter { it.runId == 1L }.all { it.status == RunStageStatus.COMPLETED },
            "완료 이후에도 열린 단계가 남아 있다: ${stageRepo.records.map { it.stage to it.status }}",
        )
        assertTrue(stageRepo.findUnsettled(1L).isEmpty(), "완료 단계가 환불 대상으로 잡힌다")
    }

    /**
     * 단계 실패 경로도 **표식을 남긴다.** 남기지 않으면 고착 복구기가 같은 단계를 미정산으로
     * 보고 인메모리 환불에 더해 **한 번 더** 돌려준다.
     */
    @Test
    fun `실패로 환불한 단계는 정산 표식이 남아 다시 환불되지 않는다`() {
        val runRepo = InMemoryPipelineRunRepository(baseRun())
        val stageRepo = InMemoryRunStageRepository()
        stubCommon()
        val failing = hookGateExecutors().map {
            if (it.stage != PipelineStage.SEGMENT) it
            else FakeStageExecutor(PipelineStage.SEGMENT) { error("AI 응답 파싱 실패") }
        }

        orchestrator(runRepo, stageRepo, InMemoryShortsClipRepository(), InMemoryClipHookRepository(), failing)
            .run(1L, PipelineStage.TRANSCRIBE)

        verify(exactly = 1) { creditService.refundAllocation(any()) }
        assertTrue(stageRepo.findUnsettled(1L).isEmpty(), "환불한 단계가 미정산으로 남아 재환불된다")
        /*
         * **환불은 정산 경로를 지나야 한다.**
         *
         * 예전에는 catch 가 인메모리 영수증으로 먼저 환불하고 표식을 나중에 세웠다. 그러면
         * 표식이 실패했을 때 복구기가 같은 단계를 한 번 더 환불한다. 표식이 환불보다 앞선다는
         * 것이 "정확히 한 번" 의 근거다.
         */
        val settled = stageRepo.records.single { it.stage == PipelineStage.SEGMENT }
        assertEquals(RunStageStatus.FAILED, settled.status)
        assertTrue(settled.refundedCredits > 0, "정산 표식 없이 환불됐다 — 복구기가 재환불한다")
    }

    /**
     * **환불이 실패하면 단계를 닫지 않는다.**
     *
     * 표식은 롤백되어 `refunded_credits = 0` 으로 돌아오지만, 여기서 상태를 `FAILED` 로 닫아
     * 버리면 `findUnsettled` 가 `RUNNING` 만 보므로 **재시도 대상에서 영구히 빠진다.**
     * 사용자는 크레딧을 영영 잃는다. 열어 둔 채로 복구기·재실행·삭제가 다시 집게 한다.
     */
    @Test
    fun `환불이 실패하면 단계를 열어 두어 다시 정산할 수 있게 한다`() {
        val runRepo = InMemoryPipelineRunRepository(baseRun())
        val stageRepo = InMemoryRunStageRepository()
        stubCommon()
        every { creditService.validateAndDeduct(userId, any<Int>(), AiFeature.SHORTS_SEGMENT.name) } returns
            com.ongo.application.credit.CreditAllocation.restored(userId, AiFeature.SHORTS_SEGMENT.name, 8, emptyMap())
        every { creditService.refundAllocation(any()) } throws IllegalStateException("환불 실패")

        val failing = hookGateExecutors().map {
            if (it.stage != PipelineStage.SEGMENT) it
            else FakeStageExecutor(PipelineStage.SEGMENT) { error("AI 응답 파싱 실패") }
        }
        orchestrator(runRepo, stageRepo, InMemoryShortsClipRepository(), InMemoryClipHookRepository(), failing)
            .run(1L, PipelineStage.TRANSCRIBE)

        val segment = stageRepo.findByRunIdAndStage(1L, PipelineStage.SEGMENT)!!

        /*
         * **오케스트레이터가 이 단계를 직접 닫지 않아야 한다.**
         *
         * 표식 롤백은 DB 트랜잭션의 일이고 단위 테스트에는 실제 트랜잭션이 없다(그래서 여기서
         * 페이크가 롤백을 흉내내지 않는다). 대신 이 테스트는 결함이 실제로 있던 자리 —
         * catch 가 `update(status = FAILED)` 를 이어서 부르던 것 — 을 고정한다.
         * 그 호출이 있으면 롤백된 뒤에도 단계가 닫혀 `findUnsettled` 에서 영구히 빠진다.
         */
        assertTrue(
            stageRepo.updatedStatuses.none { it.first == segment.id && it.second == RunStageStatus.FAILED },
            "환불 실패 단계를 오케스트레이터가 닫았다 — 롤백돼도 재시도 대상에서 빠진다: ${stageRepo.updatedStatuses}",
        )
        // 분해는 보존되어야 되돌릴 근거가 남는다.
        assertNotNull(segment.creditAllocation, "분해가 사라지면 되돌릴 근거가 없다")

        // 실행 자체는 실패로 끝난다 — 사용자에게는 결과가 없다는 사실을 알려야 한다.
        assertEquals(PipelineRunStatus.FAILED, runRepo.findById(1L)!!.status)
    }

    /** 정산할 것이 없는 단계(차감 전 실패)는 아무도 닫아 주지 않으므로 여기서 닫는다. */
    @Test
    fun `차감 전에 실패한 단계는 FAILED 로 닫는다`() {
        val runRepo = InMemoryPipelineRunRepository(baseRun())
        val stageRepo = InMemoryRunStageRepository()
        stubCommon()
        // 무과금처럼 분해가 없는 상태를 만든다.
        every { creditService.validateAndDeduct(userId, any<Int>(), AiFeature.SHORTS_SEGMENT.name) } returns
            com.ongo.application.credit.CreditAllocation.empty(userId, AiFeature.SHORTS_SEGMENT.name)

        val failing = hookGateExecutors().map {
            if (it.stage != PipelineStage.SEGMENT) it
            else FakeStageExecutor(PipelineStage.SEGMENT) { error("AI 응답 파싱 실패") }
        }
        orchestrator(runRepo, stageRepo, InMemoryShortsClipRepository(), InMemoryClipHookRepository(), failing)
            .run(1L, PipelineStage.TRANSCRIBE)

        val segment = stageRepo.findByRunIdAndStage(1L, PipelineStage.SEGMENT)!!
        assertEquals(RunStageStatus.FAILED, segment.status)
        assertTrue(stageRepo.findUnsettled(1L).isEmpty(), "정산할 것이 없는데 재시도 대상으로 남았다")
    }

    // ---- 파일럿 측정: 최초 실행 시각 ----

    /**
     * 재실행·재개도 `run` 을 다시 지난다.
     *
     * 그때마다 시작 시각을 덮으면 며칠 걸린 납품이 방금 시작한 납품으로 보이고,
     * 리드타임은 파일럿에서 납기를 판단하는 유일한 지표다.
     */
    @Test
    fun `재개해도 최초 실행 시각은 처음 값을 유지한다`() {
        stubCommon()
        val runRepo = InMemoryPipelineRunRepository(baseRun())
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository()
        val hookRepo = InMemoryClipHookRepository()

        val target = orchestrator(runRepo, stageRepo, clipRepo, hookRepo, emptyList())

        /*
         * SCHEDULE 부터 시작하고 예약 파라미터를 주지 않으면 루프가 즉시 멈춘다.
         * 여기서 보려는 것은 단계 실행이 아니라 진입 시점의 시작 시각 기록이다.
         */
        target.run(1L, PipelineStage.SCHEDULE)
        val first = runRepo.findById(1L)!!.startedAt
        assertNotNull(first, "최초 실행에서 시작 시각이 기록되지 않았다")

        // 같은 실행을 다시 돌린다(게이트 재개·재실행이 지나는 경로와 동일).
        target.run(1L, PipelineStage.SCHEDULE)

        assertEquals(first, runRepo.findById(1L)!!.startedAt, "재개가 최초 시작 시각을 덮어썼다")
    }

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

        // 후킹 확정(selectHooks)이 재개 전에 PENDING 으로 무장한다. 그 무장이 있어야
        // 재개 이벤트가 중복 이벤트와 구분된다.
        runRepo.update(runRepo.findById(1L)!!.copy(status = PipelineRunStatus.PENDING))

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
    fun `자동 실행은 기본 후킹을 확정하고 브라우저 게이트 없이 예약 대기로 진행한다`() {
        stubCommon()
        val runRepo = InMemoryPipelineRunRepository(baseRun().copy(autoSchedule = true))
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository(listOf(clip(11, 1)))
        val hookRepo = InMemoryClipHookRepository()
        val hook = FakeStageExecutor(PipelineStage.HOOK) {
            ShortsStageOutput(
                outputSnapshot = "{}",
                hooks = listOf(GeneratedHook(11, HookVariant.A, "자동 후킹")),
            )
        }
        val template = FakeStageExecutor(PipelineStage.TEMPLATE) { ShortsStageOutput(outputSnapshot = "{}") }
        val renderSpec = FakeStageExecutor(PipelineStage.RENDER_SPEC) {
            ShortsStageOutput(outputSnapshot = "{}", renderSpecs = mapOf(11L to "{}"))
        }
        val validate = FakeStageExecutor(PipelineStage.VALIDATE) { ShortsStageOutput(outputSnapshot = "{}") }

        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, listOf(hook, template, renderSpec, validate))
            .run(1L, PipelineStage.HOOK)

        assertEquals(PipelineRunStatus.AWAITING_SCHEDULE, runRepo.findById(1L)!!.status)
        assertEquals(ClipStatus.RENDER_READY, clipRepo.findById(11)!!.status)
        assertEquals(true, hookRepo.hooks.last { it.clipId == 11L }.selected)
        assertEquals(1, template.callCount)
        assertEquals(1, renderSpec.callCount)
        assertEquals(1, validate.callCount)
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

        // 단계 → 기능 매핑대로 차감된다 (TRANSCRIBE=STT, REFRAME/SEGMENT/SUBTITLE/HOOK).
        // 이 실행은 길이가 NULL 이라 전사도 종전 정액이다.
        verify { creditService.validateAndDeduct(userId, AiFeature.STT.creditCost, "STT") }
        verify { creditService.validateAndDeduct(userId, AiFeature.SHORTS_REFRAME.creditCost, "SHORTS_REFRAME") }
        verify { creditService.validateAndDeduct(userId, AiFeature.SHORTS_SEGMENT.creditCost, "SHORTS_SEGMENT") }
        verify { creditService.validateAndDeduct(userId, AiFeature.SHORTS_SUBTITLE.creditCost, "SHORTS_SUBTITLE") }
        verify { creditService.validateAndDeduct(userId, AiFeature.SHORTS_HOOK.creditCost, "SHORTS_HOOK") }
        verify(exactly = 5) { creditService.validateAndDeduct(userId, any<Int>(), any<String>()) }
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
            // 후킹 확정(selectHooks)이 PENDING 으로 무장한 뒤 이벤트를 내므로 재개 시점 상태는 PENDING 이다.
            baseRun().copy(clipCount = 2, transcriptText = "전사 전문"),
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
        verify { creditService.validateAndDeduct(userId, AiFeature.SHORTS_TEMPLATE.creditCost, "SHORTS_TEMPLATE") }
        verify { creditService.validateAndDeduct(userId, AiFeature.SHORTS_VALIDATE.creditCost, "SHORTS_VALIDATE") }
        verify(exactly = 2) { creditService.validateAndDeduct(userId, any<Int>(), any<String>()) }
        assertEquals(0, stageRepo.records.first { it.stage == PipelineStage.RENDER_SPEC }.creditCost)
        assertNull(stageRepo.records.first { it.stage == PipelineStage.RENDER_SPEC }.aiProvider)
    }

    // ---- 1. 상태 전이: 종료 (SCHEDULE → COMPLETED) ----

    @Test
    fun `예약 파라미터와 함께 SCHEDULE을 실행하면 클립이 예약되고 COMPLETED로 끝난다`() {
        stubCommon()
        val runRepo = InMemoryPipelineRunRepository(baseRun().copy(clipCount = 3))
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
        verify(exactly = 0) { creditService.validateAndDeduct(any(), any<Int>(), any<String>()) }
        assertEquals(0, stageRepo.records.first { it.stage == PipelineStage.SCHEDULE }.creditCost)
    }

    @Test
    fun `SCHEDULE 일부 실패는 PARTIALLY_COMPLETED로 끝나고 재게시 안내를 남긴다`() {
        stubCommon()
        val runRepo = InMemoryPipelineRunRepository(baseRun())
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository(emptyList())
        val hookRepo = InMemoryClipHookRepository()
        val scheduleExecutor = object : ShortsStageExecutor {
            override val stage = PipelineStage.SCHEDULE

            override fun execute(context: ShortsStageContext) = ShortsStageOutput(
                outputSnapshot = "{}",
                scheduleOutcome = ScheduleOutcome.PARTIAL,
            )
        }

        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, listOf(scheduleExecutor))
            .run(1L, PipelineStage.SCHEDULE, ScheduleParams(Instant.parse("2026-03-01T09:00:00Z"), 6, listOf("YOUTUBE")))

        val result = runRepo.findById(1L)!!
        assertEquals(PipelineRunStatus.PARTIALLY_COMPLETED, result.status)
        assertTrue(result.errorMessage!!.contains("일부 쇼츠 플랫폼"))
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
        verify { creditService.validateAndDeduct(userId, AiFeature.STT.creditCost, "STT") }
        verify { creditService.validateAndDeduct(userId, AiFeature.SHORTS_REFRAME.creditCost, "SHORTS_REFRAME") }
        verify { creditService.validateAndDeduct(userId, AiFeature.SHORTS_SEGMENT.creditCost, "SHORTS_SEGMENT") }
        /*
         * 환불은 **저장된 분해**로 한다. 금액만 넘기면 구매분이 무료분으로 바뀐다
         * (CreditAllocation 참고). 차감 당시 객체와 같은 인스턴스가 아니므로 금액으로 비교한다.
         */
        val refunded = slot<com.ongo.application.credit.CreditAllocation>()
        verify(exactly = 1) { creditService.refundAllocation(capture(refunded)) }
        assertEquals(AiFeature.SHORTS_SEGMENT.creditCost, refunded.captured.total)
    }

    /**
     * 환불 자체가 실패하는 경우.
     *
     * 예전에는 결과를 버리는 `runCatching` 이라 실패가 흔적 없이 사라졌다. 사용자는 결과도
     * 크레딧도 잃고, 운영은 그런 일이 있었다는 것조차 모른다. 이제 복구에 필요한 값
     * (runId·stage·userId·금액)을 error 로 남긴다.
     *
     * 로그 내용을 Logback appender 로 직접 확인한다 — "삼키지 않는다"는 주장을 문자열로
     * 증명하지 않으면 다음 리팩터링에서 그대로 사라진다.
     */
    @Test
    fun `환불이 실패해도 파이프라인을 끝내고 복구 가능한 로그를 남긴다`() {
        stubCommon()
        val runRepo = InMemoryPipelineRunRepository(baseRun())
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository()
        val hookRepo = InMemoryClipHookRepository()

        every {
            creditService.refundAllocation(any())
        } throws IllegalStateException("환불 저장 실패")

        val transcribe = FakeStageExecutor(PipelineStage.TRANSCRIBE) {
            ShortsStageOutput(outputSnapshot = """{"text":"t","segments":[]}""", transcriptText = "t")
        }
        val reframe = FakeStageExecutor(PipelineStage.REFRAME) { ShortsStageOutput(outputSnapshot = "{}") }
        val segment = FakeStageExecutor(PipelineStage.SEGMENT) { throw RuntimeException("AI 응답 파싱 실패") }

        val logger = org.slf4j.LoggerFactory.getLogger(ShortsPipelineOrchestrator::class.java)
            as ch.qos.logback.classic.Logger
        val appender = ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            orchestrator(runRepo, stageRepo, clipRepo, hookRepo, listOf(transcribe, reframe, segment))
                .run(1L, PipelineStage.TRANSCRIBE)
        } finally {
            logger.detachAppender(appender)
        }

        // 환불 실패가 파이프라인을 중단시키지 않는다. 상태는 정상적으로 FAILED 로 닫힌다.
        assertEquals(PipelineRunStatus.FAILED, runRepo.findById(1L)!!.status)
        assertEquals(RunStageStatus.FAILED, stageRepo.findByRunIdAndStage(1L, PipelineStage.SEGMENT)!!.status)

        val refundError = appender.list.firstOrNull {
            it.level == ch.qos.logback.classic.Level.ERROR && it.formattedMessage.contains("환불 실패")
        }
        assertNotNull(refundError, "환불 실패가 error 로 남지 않았다: ${appender.list.map { it.formattedMessage }}")
        // 운영이 수기로 복구하려면 누구에게 얼마를 돌려줄지가 로그에 있어야 한다.
        val message = refundError.formattedMessage
        assertTrue(message.contains("runId=1"), message)
        assertTrue(message.contains("stage=SEGMENT"), message)
        assertTrue(message.contains("userId=$userId"), message)
        assertTrue(message.contains("amount=${AiFeature.SHORTS_SEGMENT.creditCost}"), message)
    }

    /*
     * 전사 원가는 길이에 정비례한다(조각마다 모델을 부른다). 정액으로 매기면 긴 원본이
     * 그대로 손실이 되므로, 실행에 고정된 길이에서 금액을 계산해야 한다.
     */
    @Test
    fun `긴 원본의 전사는 길이에 비례해 차감하고 기록도 그 금액이다`() {
        stubCommon()
        val sixtyMinutes = 60L * 60 * 1000
        val runRepo = InMemoryPipelineRunRepository(baseRun(sourceDurationMs = sixtyMinutes))
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository()
        val hookRepo = InMemoryClipHookRepository()

        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, hookGateExecutors())
            .run(1L, PipelineStage.TRANSCRIBE)

        val expected = ShortsPipelineCreditRequirements.transcribeCredits(sixtyMinutes)
        verify(exactly = 1) { creditService.validateAndDeduct(userId, expected, "STT") }
        assertEquals(expected, stageRepo.records.first { it.stage == PipelineStage.TRANSCRIBE }.creditCost)
    }

    /*
     * 재실행도 같은 진입점을 지난다. 실행에 고정된 길이를 다시 읽으므로 첫 실행과 같은
     * 금액이 나와야 한다 — 다시 재서 다른 금액이 나오면 인용한 적 없는 청구다.
     */
    @Test
    fun `전사 재실행도 같은 고정 길이로 같은 금액을 차감한다`() {
        stubCommon()
        val sixtyMinutes = 60L * 60 * 1000
        val runRepo = InMemoryPipelineRunRepository(baseRun(sourceDurationMs = sixtyMinutes))
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository()
        val hookRepo = InMemoryClipHookRepository()
        val expected = ShortsPipelineCreditRequirements.transcribeCredits(sixtyMinutes)

        repeat(2) {
            // 재실행 API(rerunStage)가 상태를 PENDING 으로 되돌린 뒤 이벤트를 낸다.
            // 그 무장을 빼면 두 번째 호출은 중복 이벤트와 구분되지 않는다.
            runRepo.update(runRepo.findById(1L)!!.copy(status = PipelineRunStatus.PENDING))
            orchestrator(runRepo, stageRepo, clipRepo, hookRepo, hookGateExecutors())
                .run(1L, PipelineStage.TRANSCRIBE)
        }

        verify(exactly = 2) { creditService.validateAndDeduct(userId, expected, "STT") }
    }

    /*
     * 환불이 단가로 고정돼 있으면 긴 원본에서 낸 것보다 적게 돌려받는다. 차감과 환불은
     * 반드시 같은 값이어야 한다.
     */
    @Test
    fun `긴 원본의 전사가 실패하면 차감한 만큼 그대로 환불한다`() {
        stubCommon()
        val sixtyMinutes = 60L * 60 * 1000
        val runRepo = InMemoryPipelineRunRepository(baseRun(sourceDurationMs = sixtyMinutes))
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository()
        val hookRepo = InMemoryClipHookRepository()
        val transcribe = FakeStageExecutor(PipelineStage.TRANSCRIBE) {
            throw RuntimeException("전사 실패")
        }

        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, listOf(transcribe)).run(1L, PipelineStage.TRANSCRIBE)

        val expected = ShortsPipelineCreditRequirements.transcribeCredits(sixtyMinutes)
        /*
         * 환불은 이제 **저장된 분해로 복원한** 영수증을 쓴다. 차감 당시의 객체와 같은
         * 인스턴스가 아니므로 금액과 출처로 비교한다 — 실제 계약은 그쪽이다.
         */
        val refunded = slot<com.ongo.application.credit.CreditAllocation>()
        verify(exactly = 1) { creditService.refundAllocation(capture(refunded)) }
        assertEquals(expected, refunded.captured.total)
    }

    /*
     * 길이에 비례하는 것은 전사뿐이다. 다른 단계까지 비례하면 원가와 무관한 곳에서
     * 요금이 오른다.
     */
    @Test
    fun `긴 원본이라도 전사 외 단계는 단가 그대로다`() {
        stubCommon()
        val sixtyMinutes = 60L * 60 * 1000
        val runRepo = InMemoryPipelineRunRepository(baseRun(sourceDurationMs = sixtyMinutes))
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository()
        val hookRepo = InMemoryClipHookRepository()

        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, hookGateExecutors())
            .run(1L, PipelineStage.TRANSCRIBE)

        verify(exactly = 1) {
            creditService.validateAndDeduct(userId, AiFeature.SHORTS_REFRAME.creditCost, "SHORTS_REFRAME")
        }
        assertEquals(
            AiFeature.SHORTS_REFRAME.creditCost,
            stageRepo.records.first { it.stage == PipelineStage.REFRAME }.creditCost,
        )
    }

    /* 이 필드 도입 이전 실행. 소급 측정하지 않고 종전 정액을 그대로 청구한다. */
    @Test
    fun `길이가 없는 기존 실행은 전사도 종전 정액이다`() {
        stubCommon()
        val runRepo = InMemoryPipelineRunRepository(baseRun(sourceDurationMs = null))
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository()
        val hookRepo = InMemoryClipHookRepository()

        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, hookGateExecutors())
            .run(1L, PipelineStage.TRANSCRIBE)

        verify(exactly = 1) { creditService.validateAndDeduct(userId, AiFeature.STT.creditCost, "STT") }
        assertEquals(
            AiFeature.STT.creditCost,
            stageRepo.records.first { it.stage == PipelineStage.TRANSCRIBE }.creditCost,
        )
    }

    @Test
    fun `크레딧 차감 전에 실패하면 환불하지 않는다`() {
        stubCommon()
        val runRepo = InMemoryPipelineRunRepository(baseRun())
        val stageRepo = InMemoryRunStageRepository()
        val clipRepo = InMemoryShortsClipRepository()
        val hookRepo = InMemoryClipHookRepository()

        // 차감 자체가 실패 (크레딧 부족 등)
        every {
            creditService.validateAndDeduct(userId, AiFeature.STT.creditCost, "STT")
        } throws RuntimeException("크레딧 부족")
        val transcribe = FakeStageExecutor(PipelineStage.TRANSCRIBE) { ShortsStageOutput(outputSnapshot = "{}") }

        orchestrator(runRepo, stageRepo, clipRepo, hookRepo, listOf(transcribe)).run(1L, PipelineStage.TRANSCRIBE)

        assertEquals(PipelineRunStatus.FAILED, runRepo.findById(1L)!!.status)
        assertEquals(0, transcribe.callCount)
        verify(exactly = 0) { creditService.refundAllocation(any()) }
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
        verify(exactly = 0) { creditService.validateAndDeduct(any(), any<Int>(), any<String>()) }
    }
}
