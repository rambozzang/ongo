package com.ongo.application.ugc.shorts

import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.PipelineRunStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 고착된 쇼츠 실행을 **재시도 가능한 상태로만** 되돌리는지 고정한다.
 *
 * ## 무엇을 막는가
 *
 * 오케스트레이터는 `@Transactional` 이 아니라 단계별 크레딧 차감이 즉시 커밋된다. 프로세스가
 * 단계 도중 죽으면 차감은 확정돼 있고 환불은 실행되지 않으며, 상태는 `RUNNING` 으로 남는다.
 * 그 상태에서는 새 이벤트도(확보 조건이 `PENDING`) 재실행 API 도(`RUNNING` 거절) 통하지 않아
 * 사용자는 실행을 지우고 처음부터 다시 결제하는 수밖에 없었다.
 *
 * ## 무엇을 하지 않는가 — 이 테스트의 절반
 *
 * 복구는 **아무것도 다시 실행하지 않는다.** `PENDING` 으로 되돌리면 다음 이벤트가 곧바로
 * 확보해 자동 재실행되는데, 진척 신호가 단계 경계에서만 갱신되므로 오래 걸리는 단계를 죽은
 * 것으로 오인하면 살아 있는 작업과 겹쳐 **같은 단계가 두 번 청구된다.**
 * 그래서 `FAILED` 로만 되돌리고, 다시 돌릴지는 사용자가 정한다.
 */
class ShortsRunRecoverySchedulerTest {

    private val runRepository = mockk<PipelineRunRepository>(relaxed = true)
    private val orchestrator = mockk<ShortsPipelineOrchestrator>()
    private val creditService = mockk<com.ongo.application.credit.CreditService>(relaxed = true)
    private val runStageRepository = mockk<com.ongo.domain.ugc.shorts.RunStageRepository>(relaxed = true)

    private val staleAfterMs = 7_200_000L
    private val now = Instant.now()

    /** 정산은 실제 구현을 쓴다 — 목으로 바꾸면 "CAS 뒤에만 환불" 배선이 검증되지 않는다. */
    private val stageCreditService = ShortsStageCreditService(creditService, runStageRepository)

    private fun scheduler() =
        ShortsRunRecoveryScheduler(runRepository, orchestrator, stageCreditService, staleAfterMs)

    private fun stuckRun(
        id: Long = 1L,
        updatedAt: Instant = now.minusMillis(staleAfterMs + 60_000),
        version: Long = 7L,
    ) = PipelineRun(
        id = id, workspaceId = 10L, userId = 1L, sourceVideoId = 5L,
        status = PipelineRunStatus.RUNNING, updatedAt = updatedAt, version = version,
    )

    private fun given(runs: List<PipelineRun>, activeHere: Set<Long> = emptySet()) {
        every { runRepository.findByStatus(PipelineRunStatus.RUNNING, any()) } returns runs
        every { orchestrator.isActiveInThisProcess(any()) } answers { firstArg<Long>() in activeHere }
    }

    /** **핵심.** 고착된 실행은 사용자가 다시 시도할 수 있어야 한다. */
    @Test
    @DisplayName("진척이 멈춘 실행을 재시도 가능한 FAILED 로 되돌린다")
    fun recoversStuckRun() {
        given(listOf(stuckRun()))

        scheduler().recoverStuckRuns()

        verify(exactly = 1) {
            runRepository.failStale(1L, 7L, ShortsRunRecoveryScheduler.STUCK_REASON)
        }
    }

    /**
     * **복구는 절대 다시 실행하지 않는다.**
     *
     * `FAILED` 는 `claimRunning` 의 조건(`PENDING`)이 아니므로 자동 재실행이 붙을 수 없다.
     * 복구기가 상태를 `PENDING` 으로 바꾸거나 이벤트를 내면 이 단정이 깨진다.
     */
    @Test
    @DisplayName("복구는 실행을 다시 돌리지 않는다")
    fun recoveryNeverReExecutes() {
        given(listOf(stuckRun()))

        scheduler().recoverStuckRuns()

        // 오케스트레이터에 물어보는 것은 생존 여부뿐이다.
        verify(exactly = 1) { orchestrator.isActiveInThisProcess(1L) }
        verify(exactly = 0) { orchestrator.run(any(), any(), any()) }
        // 상태를 직접 쓰지 않는다 — 조건부 갱신만 쓴다.
        verify(exactly = 0) { runRepository.update(any()) }
        verify(exactly = 0) { runRepository.claimRunning(any()) }
    }

    /** 이 프로세스가 들고 있는 실행은 살아 있다. 건드리면 이중 청구로 이어진다. */
    @Test
    @DisplayName("이 프로세스가 실행 중인 run 은 건드리지 않는다")
    fun skipsRunsAliveInThisProcess() {
        given(listOf(stuckRun()), activeHere = setOf(1L))

        scheduler().recoverStuckRuns()

        verify(exactly = 0) { runRepository.failStale(any(), any(), any()) }
    }

    /** 아직 진척이 있었다면 고착이 아니다. */
    @Test
    @DisplayName("최근에 진척이 있었으면 건드리지 않는다")
    fun skipsRecentlyProgressedRuns() {
        given(listOf(stuckRun(updatedAt = now.minusMillis(staleAfterMs / 2))))

        scheduler().recoverStuckRuns()

        verify(exactly = 0) { runRepository.failStale(any(), any(), any()) }
    }

    /**
     * 관측한 `version` 을 그대로 넘겨야 조건부 갱신이 의미를 갖는다. 다른 값을 넘기면
     * 살아 있는 작업의 진행을 놓치고 덮어쓴다.
     */
    @Test
    @DisplayName("관측한 version 을 그대로 조건으로 넘긴다")
    fun passesObservedVersionAsTheGuard() {
        given(listOf(stuckRun(version = 42L)))

        scheduler().recoverStuckRuns()

        verify(exactly = 1) { runRepository.failStale(1L, 42L, any()) }
    }

    /** 한 건이 실패해도 나머지는 복구되어야 한다. */
    @Test
    @DisplayName("한 건이 실패해도 나머지를 계속 복구한다")
    fun oneFailureDoesNotStopTheRest() {
        given(listOf(stuckRun(id = 1L), stuckRun(id = 2L)))
        every { runRepository.failStale(1L, any(), any()) } throws IllegalStateException("DB 오류")
        every { runRepository.failStale(2L, any(), any()) } returns true

        scheduler().recoverStuckRuns()

        verify(exactly = 1) { runRepository.failStale(2L, any(), any()) }
    }

    /* ── 실패한 실행의 미정산 재시도 ─────────────────────────────────── */

    /**
     * **정산 실패는 재시도될 수 있어야 한다.**
     *
     * 정산이 실패하면 표식이 롤백되어 단계는 `RUNNING` · 미정산으로 남는다. 그런데 그 시점의
     * 실행은 이미 `FAILED` 라 `RUNNING` 실행만 훑는 복구 경로에는 **다시 잡히지 않는다.**
     * 이 훑기가 없으면 사용자가 재실행·삭제를 누르기 전까지 크레딧이 묶인 채 남는다.
     */
    @Test
    @DisplayName("이미 실패한 실행의 미정산 단계를 다시 정산한다")
    fun retriesSettlementForAlreadyFailedRuns() {
        val failedRun = stuckRun(id = 5L).copy(status = PipelineRunStatus.FAILED)
        every { runRepository.findFailedWithUnsettledStages(any()) } returns listOf(failedRun)
        every { orchestrator.isActiveInThisProcess(any()) } returns false
        every { runStageRepository.findUnsettled(5L, 0) } returns emptyList()

        scheduler().settleFailedRuns()

        verify(exactly = 1) { runStageRepository.findUnsettled(5L, 0) }
    }

    /** 살아 있는 작업은 여기서도 건드리지 않는다. */
    @Test
    @DisplayName("이 프로세스가 실행 중이면 정산 훑기에서도 건너뛴다")
    fun sweepSkipsRunsAliveInThisProcess() {
        val failedRun = stuckRun(id = 5L).copy(status = PipelineRunStatus.FAILED)
        every { runRepository.findFailedWithUnsettledStages(any()) } returns listOf(failedRun)
        every { orchestrator.isActiveInThisProcess(5L) } returns true

        scheduler().settleFailedRuns()

        verify(exactly = 0) { runStageRepository.findUnsettled(any(), any()) }
    }

    /** 한 실행의 정산이 실패해도 훑기가 멈추면 안 된다 — 다음 tick 에 다시 시도한다. */
    @Test
    @DisplayName("정산 실패가 훑기를 중단시키지 않는다")
    fun sweepContinuesAfterOneFailure() {
        val runs = listOf(5L, 6L).map { stuckRun(id = it).copy(status = PipelineRunStatus.FAILED) }
        every { runRepository.findFailedWithUnsettledStages(any()) } returns runs
        every { orchestrator.isActiveInThisProcess(any()) } returns false
        every { runStageRepository.findUnsettled(5L, 0) } throws IllegalStateException("DB 오류")
        every { runStageRepository.findUnsettled(6L, 0) } returns emptyList()

        scheduler().settleFailedRuns()

        verify(exactly = 1) { runStageRepository.findUnsettled(6L, 0) }
    }

    /**
     * **훑기는 상태만 보고 앞에서 끊으면 안 된다.**
     *
     * `FAILED` 실행은 지워지지 않고 쌓인다. 상태만으로 N 건을 끊으면 정산이 끝난 오래된
     * 실행들이 그 자리를 영원히 차지해, 정작 환불이 밀린 실행에는 도달하지 못한 채 훑기가
     * 조용히 무력해진다. 로그도 남지 않아 알아채기 어렵다.
     */
    @Test
    @DisplayName("정산 훑기는 미정산이 남은 실행만 후보로 삼는다")
    fun sweepQueriesOnlyRunsThatStillNeedSettlement() {
        every { runRepository.findFailedWithUnsettledStages(any()) } returns emptyList()

        scheduler().settleFailedRuns()

        verify(exactly = 1) { runRepository.findFailedWithUnsettledStages(any()) }
        verify(exactly = 0) { runRepository.findByStatus(PipelineRunStatus.FAILED, any()) }
    }

    /** 사용자에게 그대로 보이는 문구다. 없는 사실(환불)을 말하지 않는다. */
    @Test
    @DisplayName("복구 사유는 지금 할 수 있는 일만 말한다")
    fun reasonStatesOnlyWhatIsTrue() {
        val reason = ShortsRunRecoveryScheduler.STUCK_REASON

        assert(reason.contains("다시 실행")) { reason }
        assert(!reason.contains("환불")) { "환불하지 않는데 환불했다고 말한다: $reason" }
        assert(!reason.contains("복구되었습니다")) { "자동 복구된 것처럼 말한다: $reason" }
    }
}
