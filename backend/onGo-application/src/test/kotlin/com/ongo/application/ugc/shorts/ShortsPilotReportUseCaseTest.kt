package com.ongo.application.ugc.shorts

import com.ongo.application.ugc.shorts.dto.ShortsPilotLimitation
import com.ongo.application.ugc.shorts.dto.ShortsPilotReportState
import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.ShortsPilotActorType
import com.ongo.domain.ugc.shorts.ShortsPilotEvent
import com.ongo.domain.ugc.shorts.ShortsPilotEventRepository
import com.ongo.domain.ugc.shorts.ShortsPilotEventType
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 파일럿 측정 보고.
 *
 * 이 보고의 가치는 "무엇을 아는가"보다 **"모르는 것을 모른다고 말하는가"** 에 있다.
 * 아직 시작 안 한 실행을 0ms 로, 입력 없는 시간을 0분으로 세면 파일럿이 실제보다
 * 좋아 보이고 그 숫자로 사업 판단을 하게 된다.
 */
@ExtendWith(MockKExtension::class)
class ShortsPilotReportUseCaseTest {

    @MockK lateinit var pipelineRunRepository: PipelineRunRepository
    @MockK lateinit var pilotEventRepository: ShortsPilotEventRepository

    @InjectMockKs lateinit var useCase: ShortsPilotReportUseCase

    private val t0 = Instant.parse("2026-08-01T00:00:00Z")

    private fun run(id: Long, startedAt: Instant? = null, deliveredAt: Instant? = null) = PipelineRun(
        id = id,
        workspaceId = 5L,
        userId = 3L,
        sourceVideoId = 99L,
        createdAt = t0,
        startedAt = startedAt,
        deliveredAt = deliveredAt,
    )

    private fun event(
        runId: Long,
        type: ShortsPilotEventType,
        minutes: Int? = null,
        amountKrw: Long? = null,
    ) = ShortsPilotEvent(
        runId = runId,
        eventType = type,
        actorType = ShortsPilotActorType.SYSTEM,
        operatorMinutes = minutes,
        amountKrw = amountKrw,
    )

    // ---- 단위경제: 운영자가 확인한 매출·외부원가 ----

    @Test
    fun `매출과 외부원가를 합산하고 기여이익을 계산한다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L)
        every { pilotEventRepository.findByRunIds(any()) } returns listOf(
            event(1L, ShortsPilotEventType.PILOT_ENROLLED),
            // 분할 청구는 행을 더 쌓아 표현한다.
            event(1L, ShortsPilotEventType.OPERATOR_REVENUE_LOGGED, amountKrw = 30_000),
            event(1L, ShortsPilotEventType.OPERATOR_REVENUE_LOGGED, amountKrw = 20_000),
            event(1L, ShortsPilotEventType.OPERATOR_EXTERNAL_COST_LOGGED, amountKrw = 8_000),
            event(1L, ShortsPilotEventType.OPERATOR_TIME_LOGGED, minutes = 120),
        )
        every { pipelineRunRepository.findByIds(any()) } returns listOf(run(1L))

        val row = useCase.report().runs.single()

        assertEquals(50_000L, row.operatorReportedRevenueKrw)
        assertEquals(8_000L, row.operatorReportedExternalCostKrw)
        assertEquals(42_000L, row.contributionExcludingExternalCostKrw)
        // 120분 = 2시간 → 42,000 / 2
        assertEquals(21_000L, row.contributionPerOperatorHourKrw)
    }

    /*
     * 매출만 적힌 실행을 기여이익으로 계산하면 이익률 100% 로 보인다. 파일럿에서 가장
     * 위험한 오독이라, 한쪽만 있으면 모른다고 둔다.
     */
    @Test
    fun `매출이나 외부원가 중 하나만 기록되면 기여이익은 null 이다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L, 2L)
        every { pilotEventRepository.findByRunIds(any()) } returns listOf(
            event(1L, ShortsPilotEventType.OPERATOR_REVENUE_LOGGED, amountKrw = 50_000),
            event(2L, ShortsPilotEventType.OPERATOR_EXTERNAL_COST_LOGGED, amountKrw = 8_000),
        )
        every { pipelineRunRepository.findByIds(any()) } returns listOf(run(1L), run(2L))

        val report = useCase.report()

        val revenueOnly = report.runs.single { it.runId == 1L }
        assertEquals(50_000L, revenueOnly.operatorReportedRevenueKrw)
        assertNull(revenueOnly.operatorReportedExternalCostKrw)
        assertNull(revenueOnly.contributionExcludingExternalCostKrw)
        assertNull(revenueOnly.contributionPerOperatorHourKrw)

        val costOnly = report.runs.single { it.runId == 2L }
        assertNull(costOnly.contributionExcludingExternalCostKrw)

        val summary = assertNotNull(report.summary)
        // 합계는 한쪽만 있는 실행도 각자 더하되, 기여이익은 둘 다 있는 실행이 0건이라 null.
        assertEquals(50_000L, summary.totalOperatorReportedRevenueKrw)
        assertEquals(8_000L, summary.totalOperatorReportedExternalCostKrw)
        assertNull(summary.totalContributionExcludingExternalCostKrw)
        assertEquals(0, summary.contributionObservedRunCount)
    }

    /* 사람 시간을 모르는 채 시간당 수치를 내면 계산이 아니라 창작이다. */
    @Test
    fun `투입 시간 기록이 없으면 시간당 기여이익은 null 이다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L)
        every { pilotEventRepository.findByRunIds(any()) } returns listOf(
            event(1L, ShortsPilotEventType.OPERATOR_REVENUE_LOGGED, amountKrw = 50_000),
            event(1L, ShortsPilotEventType.OPERATOR_EXTERNAL_COST_LOGGED, amountKrw = 8_000),
        )
        every { pipelineRunRepository.findByIds(any()) } returns listOf(run(1L))

        val row = useCase.report().runs.single()

        assertEquals(42_000L, row.contributionExcludingExternalCostKrw)
        assertNull(row.contributionPerOperatorHourKrw, "시간 기록이 없는데 시간당 수치를 만들었다")
    }

    @Test
    fun `기록이 없으면 매출과 외부원가는 0 원이 아니라 null 이다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L)
        every { pilotEventRepository.findByRunIds(any()) } returns emptyList()
        every { pipelineRunRepository.findByIds(any()) } returns listOf(run(1L))

        val report = useCase.report()
        val row = report.runs.single()

        assertNull(row.operatorReportedRevenueKrw)
        assertNull(row.operatorReportedExternalCostKrw)
        assertNull(row.contributionExcludingExternalCostKrw)
        assertNull(row.contributionPerOperatorHourKrw)

        val summary = assertNotNull(report.summary)
        assertNull(summary.totalOperatorReportedRevenueKrw)
        assertNull(summary.totalOperatorReportedExternalCostKrw)
        assertNull(summary.totalContributionExcludingExternalCostKrw)
    }

    /* 외부원가가 매출을 넘는 역마진도 그대로 보여야 한다. 그걸 보려고 재는 것이다. */
    @Test
    fun `역마진이면 기여이익이 음수로 나온다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L)
        every { pilotEventRepository.findByRunIds(any()) } returns listOf(
            event(1L, ShortsPilotEventType.OPERATOR_REVENUE_LOGGED, amountKrw = 5_000),
            event(1L, ShortsPilotEventType.OPERATOR_EXTERNAL_COST_LOGGED, amountKrw = 12_000),
            event(1L, ShortsPilotEventType.OPERATOR_TIME_LOGGED, minutes = 60),
        )
        every { pipelineRunRepository.findByIds(any()) } returns listOf(run(1L))

        val row = useCase.report().runs.single()

        assertEquals(-7_000L, row.contributionExcludingExternalCostKrw)
        assertEquals(-7_000L, row.contributionPerOperatorHourKrw)
    }

    /* 등록되지 않은 실행이 섞이면 "파일럿 성과"가 아니라 그냥 전체 사용 통계가 된다. */
    @Test
    fun `파일럿에 등록된 실행만 보고에 넣는다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L)
        every { pilotEventRepository.findByRunIds(listOf(1L)) } returns
            listOf(event(1L, ShortsPilotEventType.PILOT_ENROLLED))
        every { pipelineRunRepository.findByIds(listOf(1L)) } returns listOf(run(1L))

        val report = useCase.report()

        assertEquals(listOf(1L), report.runs.map { it.runId })
        // 등록 목록에 없는 실행은 조회 대상 자체가 아니다.
        verify(exactly = 1) { pilotEventRepository.findByRunIds(listOf(1L)) }
        verify(exactly = 1) { pipelineRunRepository.findByIds(listOf(1L)) }
    }

    @Test
    fun `이벤트별 횟수와 운영자 투입 시간을 집계한다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L, 2L)
        every { pilotEventRepository.findByRunIds(listOf(1L, 2L)) } returns listOf(
            event(1L, ShortsPilotEventType.PILOT_ENROLLED),
            event(1L, ShortsPilotEventType.STAGE_RERUN),
            event(1L, ShortsPilotEventType.STAGE_RERUN),
            event(1L, ShortsPilotEventType.RENDER_ATTEMPT_FAILED),
            // 한 실행에 여러 번 쌓이며 합산한다.
            event(1L, ShortsPilotEventType.OPERATOR_TIME_LOGGED, minutes = 30),
            event(1L, ShortsPilotEventType.OPERATOR_TIME_LOGGED, minutes = 15),
            event(2L, ShortsPilotEventType.PILOT_ENROLLED),
            event(2L, ShortsPilotEventType.RENDER_ATTEMPT_FAILED),
            event(2L, ShortsPilotEventType.RENDER_ATTEMPT_FAILED),
        )
        every { pipelineRunRepository.findByIds(listOf(1L, 2L)) } returns listOf(run(1L), run(2L))

        val report = useCase.report()

        val first = report.runs.single { it.runId == 1L }
        assertEquals(2, first.stageRerunCount)
        assertEquals(1, first.renderAttemptFailureCount)
        assertEquals(45, first.operatorMinutes)

        val second = report.runs.single { it.runId == 2L }
        assertEquals(0, second.stageRerunCount)
        assertEquals(2, second.renderAttemptFailureCount)
        // 입력이 없으면 "0분 썼다"가 아니라 "모른다"다.
        assertNull(second.operatorMinutes)

        val summary = assertNotNull(report.summary)
        assertEquals(2, summary.enrolledRunCount)
        assertEquals(2, summary.totalStageReruns)
        assertEquals(3, summary.totalRenderAttemptFailures)
        assertEquals(45, summary.totalOperatorMinutes)
    }

    /*
     * 시작만 있고 납품이 없는 실행을 "아직 0ms"로 세면 평균이 짧아져 납기가 좋아 보인다.
     */
    @Test
    fun `시작과 납품이 모두 있는 실행만 리드타임을 계산한다`() {
        val delivered = run(1L, startedAt = t0, deliveredAt = t0.plusSeconds(600))
        val startedOnly = run(2L, startedAt = t0)
        val notStarted = run(3L)
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L, 2L, 3L)
        every { pilotEventRepository.findByRunIds(any()) } returns emptyList()
        every { pipelineRunRepository.findByIds(any()) } returns listOf(delivered, startedOnly, notStarted)

        val report = useCase.report()

        assertEquals(600_000L, report.runs.single { it.runId == 1L }.leadTimeMs)
        assertNull(report.runs.single { it.runId == 2L }.leadTimeMs)
        assertNull(report.runs.single { it.runId == 3L }.leadTimeMs)

        val summary = assertNotNull(report.summary)
        assertEquals(3, summary.enrolledRunCount)
        assertEquals(2, summary.startedRunCount)
        assertEquals(1, summary.deliveredRunCount)

        val leadTime = assertNotNull(summary.leadTime)
        // 미관측 실행을 0 으로 끼워 넣지 않는다 — 관측된 1건만의 평균이다.
        assertEquals(1, leadTime.observedRunCount)
        assertEquals(600_000L, leadTime.averageMs)
        assertEquals(600_000L, leadTime.minMs)
        assertEquals(600_000L, leadTime.maxMs)
    }

    @Test
    fun `리드타임을 관측한 실행이 없으면 리드타임 summary 는 null 이다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L)
        every { pilotEventRepository.findByRunIds(any()) } returns emptyList()
        every { pipelineRunRepository.findByIds(any()) } returns listOf(run(1L))

        val summary = assertNotNull(useCase.report().summary)

        assertNull(summary.leadTime, "관측이 없는데 리드타임 요약을 만들었다")
        assertNull(summary.totalOperatorMinutes)
    }

    /*
     * 0 으로 채운 summary 는 "실패율 0%, 재실행 0건"으로 읽혀서 아직 시작도 안 한
     * 파일럿이 완벽해 보인다.
     */
    @Test
    fun `등록된 실행이 없으면 NO_DATA 이고 summary 는 null 이다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns emptyList()

        val report = useCase.report()

        assertEquals(ShortsPilotReportState.NO_DATA, report.state)
        assertNull(report.summary, "등록이 없는데 0 으로 채운 집계를 만들었다")
        assertTrue(report.runs.isEmpty())
        // 등록이 없으면 이벤트·실행을 더 조회할 이유가 없다.
        verify(exactly = 0) { pilotEventRepository.findByRunIds(any()) }
        verify(exactly = 0) { pipelineRunRepository.findByIds(any()) }
    }

    /* 모르는 것은 데이터 양과 무관하다. 숫자를 붙이면 측정 결과로 둔갑한다. */
    @Test
    fun `측정하지 못하는 항목을 코드로만 명시한다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns emptyList()

        val limitations = useCase.report().limitations

        assertEquals(
            listOf(
                ShortsPilotLimitation.PAYMENT_NOT_ATTRIBUTED,
                ShortsPilotLimitation.REPEAT_PURCHASE_NOT_MEASURED,
                ShortsPilotLimitation.ACTUAL_INFRASTRUCTURE_COST_NOT_AVAILABLE,
                // 매출·원가가 자동 연동이 아니라는 사실이 항상 응답에 실려야 한다.
                ShortsPilotLimitation.REVENUE_AND_COST_ARE_OPERATOR_REPORTED,
                ShortsPilotLimitation.LABOR_COST_NOT_INCLUDED_IN_CONTRIBUTION,
            ),
            limitations,
        )
    }

    /* 등록 이벤트는 있는데 실행 행이 사라진 경우. 없는 것을 지어내지 않는다. */
    @Test
    fun `실행 행이 없는 등록은 행을 만들지 않는다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L, 2L)
        every { pilotEventRepository.findByRunIds(any()) } returns emptyList()
        every { pipelineRunRepository.findByIds(any()) } returns listOf(run(1L))

        val report = useCase.report()

        assertEquals(listOf(1L), report.runs.map { it.runId })
        assertEquals(1, assertNotNull(report.summary).enrolledRunCount)
    }
}
