package com.ongo.application.ugc.shorts

import com.ongo.application.ugc.shorts.dto.ShortsPilotLimitation
import com.ongo.application.ugc.shorts.dto.ShortsPilotReportState
import com.ongo.application.ugc.shorts.dto.ShortsPilotReportSummary
import com.ongo.application.ugc.shorts.dto.ShortsPilotRunRow
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
import kotlin.test.assertFalse
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

    private fun run(
        id: Long,
        startedAt: Instant? = null,
        deliveredAt: Instant? = null,
        userId: Long = 3L,
    ) = PipelineRun(
        id = id,
        workspaceId = 5L,
        userId = userId,
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
        id: Long = 0,
    ) = ShortsPilotEvent(
        id = id,
        runId = runId,
        eventType = type,
        actorType = ShortsPilotActorType.SYSTEM,
        operatorMinutes = minutes,
        amountKrw = amountKrw,
    )

    /** 원본 하나를 무효화하는 취소 행. 원본은 지우지 않는다. */
    private fun reversal(runId: Long, target: Long, id: Long = 0) = ShortsPilotEvent(
        id = id,
        runId = runId,
        eventType = ShortsPilotEventType.OPERATOR_ENTRY_REVERSED,
        actorType = ShortsPilotActorType.ADMIN,
        reversesEventId = target,
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

    /* ---- 표본 왜곡: 실행 수와 고객 수는 다른 것이다 ---- */

    /**
     * 실행 10건이 고객 1명에게서 나온 표본과 10명에게서 나온 표본은 단위경제 근거로서
     * 값이 전혀 다른데, 지금까지 응답으로는 구분되지 않았다.
     */
    @Test
    fun `같은 고객의 실행 2건은 고객 1명 반복 1명으로 세고 두 행 모두 반복으로 표시한다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L, 2L)
        every { pilotEventRepository.findByRunIds(any()) } returns emptyList()
        every { pipelineRunRepository.findByIds(any()) } returns listOf(
            run(1L, userId = 3L),
            run(2L, userId = 3L),
        )

        val report = useCase.report()
        val summary = assertNotNull(report.summary)

        assertEquals(2, summary.enrolledRunCount)
        assertEquals(1, summary.enrolledCustomerCount)
        assertEquals(1, summary.repeatCustomerCount)
        assertTrue(report.runs.all { it.isRepeatCustomer }, "두 행 모두 반복 고객이어야 한다")
    }

    @Test
    fun `서로 다른 고객의 실행 2건은 고객 2명 반복 0명이고 두 행 모두 반복이 아니다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L, 2L)
        every { pilotEventRepository.findByRunIds(any()) } returns emptyList()
        every { pipelineRunRepository.findByIds(any()) } returns listOf(
            run(1L, userId = 3L),
            run(2L, userId = 4L),
        )

        val report = useCase.report()
        val summary = assertNotNull(report.summary)

        assertEquals(2, summary.enrolledRunCount)
        assertEquals(2, summary.enrolledCustomerCount)
        assertEquals(0, summary.repeatCustomerCount)
        assertTrue(report.runs.none { it.isRepeatCustomer }, "반복 고객이 없어야 한다")
    }

    /**
     * 실행 행이 사라진 등록을 세면 반복 고객 수가 실제보다 커진다. 없는 실행으로는 그
     * 고객이 몇 번 썼는지 판정할 수 없다.
     */
    @Test
    fun `실행 행이 없는 등록은 고객 수와 반복 수에서 제외한다`() {
        // 2L 은 등록돼 있지만 실행 행이 없다. 남은 것은 고객 3L 의 실행 1건뿐이다.
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L, 2L)
        every { pilotEventRepository.findByRunIds(any()) } returns emptyList()
        every { pipelineRunRepository.findByIds(any()) } returns listOf(run(1L, userId = 3L))

        val report = useCase.report()
        val summary = assertNotNull(report.summary)

        assertEquals(1, summary.enrolledCustomerCount)
        assertEquals(0, summary.repeatCustomerCount)
        assertFalse(report.runs.single().isRepeatCustomer)
    }

    /** 고객이 셋 이상 섞여도 반복 판정이 고객별로 나뉘어야 한다. */
    @Test
    fun `반복 고객과 단발 고객이 섞이면 행마다 다르게 표시한다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L, 2L, 3L)
        every { pilotEventRepository.findByRunIds(any()) } returns emptyList()
        every { pipelineRunRepository.findByIds(any()) } returns listOf(
            run(1L, userId = 3L),
            run(2L, userId = 3L),
            run(3L, userId = 9L),
        )

        val report = useCase.report()
        val summary = assertNotNull(report.summary)

        assertEquals(2, summary.enrolledCustomerCount)
        assertEquals(1, summary.repeatCustomerCount)
        assertEquals(
            mapOf(1L to true, 2L to true, 3L to false),
            report.runs.associate { it.runId to it.isRepeatCustomer },
        )
    }

    /**
     * 고객 축을 더하면서 조회가 늘면 목록 화면에서 조용히 자란다. 이 값들은 이미 읽어 온
     * runsById 로만 계산해야 한다.
     */
    @Test
    fun `고객 집계를 더해도 조회 횟수는 그대로다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L, 2L)
        every { pilotEventRepository.findByRunIds(any()) } returns emptyList()
        every { pipelineRunRepository.findByIds(any()) } returns listOf(
            run(1L, userId = 3L),
            run(2L, userId = 3L),
        )

        useCase.report()

        verify(exactly = 1) { pilotEventRepository.findEnrolledRunIds() }
        verify(exactly = 1) { pilotEventRepository.findByRunIds(listOf(1L, 2L)) }
        verify(exactly = 1) { pipelineRunRepository.findByIds(listOf(1L, 2L)) }
        verify(exactly = 0) { pipelineRunRepository.findById(any()) }
    }

    /* ---- 역분개: 취소된 수기 기록은 합계에서 빠진다 ---- */

    /**
     * 오입력을 되돌리는 유일한 경로다. 원본 행은 남아 있고 **합계에서만** 빠져야 한다.
     */
    @Test
    fun `취소된 매출은 합계에서 빠진다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L)
        every { pilotEventRepository.findByRunIds(any()) } returns listOf(
            // 3,000,000 을 잘못 넣고 취소한 뒤 300,000 을 다시 넣었다.
            event(1L, ShortsPilotEventType.OPERATOR_REVENUE_LOGGED, amountKrw = 3_000_000, id = 11L),
            reversal(1L, target = 11L, id = 12L),
            event(1L, ShortsPilotEventType.OPERATOR_REVENUE_LOGGED, amountKrw = 300_000, id = 13L),
            event(1L, ShortsPilotEventType.OPERATOR_EXTERNAL_COST_LOGGED, amountKrw = 50_000, id = 14L),
        )
        every { pipelineRunRepository.findByIds(any()) } returns listOf(run(1L))

        val row = useCase.report().runs.single()

        assertEquals(300_000L, row.operatorReportedRevenueKrw)
        assertEquals(50_000L, row.operatorReportedExternalCostKrw)
        assertEquals(250_000L, row.contributionExcludingExternalCostKrw)
    }

    @Test
    fun `취소된 투입 시간은 합계에서 빠진다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L)
        every { pilotEventRepository.findByRunIds(any()) } returns listOf(
            event(1L, ShortsPilotEventType.OPERATOR_TIME_LOGGED, minutes = 600, id = 21L),
            reversal(1L, target = 21L, id = 22L),
            event(1L, ShortsPilotEventType.OPERATOR_TIME_LOGGED, minutes = 60, id = 23L),
        )
        every { pipelineRunRepository.findByIds(any()) } returns listOf(run(1L))

        assertEquals(60, useCase.report().runs.single().operatorMinutes)
    }

    /**
     * 하나뿐인 기록을 취소하면 0 이 아니라 **미기록**으로 돌아가야 한다. 0 으로 내리면
     * "무상 제공"이나 "원가 0"으로 읽힌다 — 이 모듈이 일관되게 피해 온 오독이다.
     */
    @Test
    fun `유일한 기록을 취소하면 0 이 아니라 미기록이 된다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L)
        every { pilotEventRepository.findByRunIds(any()) } returns listOf(
            event(1L, ShortsPilotEventType.OPERATOR_REVENUE_LOGGED, amountKrw = 300_000, id = 31L),
            reversal(1L, target = 31L, id = 32L),
        )
        every { pipelineRunRepository.findByIds(any()) } returns listOf(run(1L))

        val row = useCase.report().runs.single()

        assertNull(row.operatorReportedRevenueKrw)
        assertNull(row.contributionExcludingExternalCostKrw)
    }

    /** 취소는 대상 실행에만 미쳐야 한다. 다른 실행 합계가 흔들리면 원장을 믿을 수 없다. */
    @Test
    fun `취소는 다른 실행의 합계를 바꾸지 않는다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L, 2L)
        every { pilotEventRepository.findByRunIds(any()) } returns listOf(
            event(1L, ShortsPilotEventType.OPERATOR_REVENUE_LOGGED, amountKrw = 300_000, id = 41L),
            reversal(1L, target = 41L, id = 42L),
            // 실행 2 는 id 만 다를 뿐 같은 금액이다. 취소가 id 로만 걸리는지 본다.
            event(2L, ShortsPilotEventType.OPERATOR_REVENUE_LOGGED, amountKrw = 300_000, id = 43L),
        )
        every { pipelineRunRepository.findByIds(any()) } returns listOf(run(1L), run(2L))

        val byRun = useCase.report().runs.associateBy { it.runId }

        assertNull(byRun.getValue(1L).operatorReportedRevenueKrw)
        assertEquals(300_000L, byRun.getValue(2L).operatorReportedRevenueKrw)
    }

    /** 자동 이벤트는 취소 대상이 아니므로 취소 행이 있어도 회차가 줄면 안 된다. */
    @Test
    fun `취소 행이 있어도 자동 이벤트 집계는 그대로다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L)
        every { pilotEventRepository.findByRunIds(any()) } returns listOf(
            event(1L, ShortsPilotEventType.STAGE_RERUN, id = 51L),
            event(1L, ShortsPilotEventType.RENDER_ATTEMPT_FAILED, id = 52L),
            event(1L, ShortsPilotEventType.OPERATOR_REVENUE_LOGGED, amountKrw = 300_000, id = 53L),
            reversal(1L, target = 53L, id = 54L),
        )
        every { pipelineRunRepository.findByIds(any()) } returns listOf(run(1L))

        val row = useCase.report().runs.single()

        assertEquals(1, row.stageRerunCount)
        assertEquals(1, row.renderAttemptFailureCount)
    }

    /** 취소를 더해도 조회는 늘지 않아야 한다. 이미 가져온 이벤트로만 판정한다. */
    @Test
    fun `역분개 처리가 조회를 늘리지 않는다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L)
        every { pilotEventRepository.findByRunIds(any()) } returns listOf(
            event(1L, ShortsPilotEventType.OPERATOR_REVENUE_LOGGED, amountKrw = 300_000, id = 61L),
            reversal(1L, target = 61L, id = 62L),
        )
        every { pipelineRunRepository.findByIds(any()) } returns listOf(run(1L))

        useCase.report()

        verify(exactly = 1) { pilotEventRepository.findEnrolledRunIds() }
        verify(exactly = 1) { pilotEventRepository.findByRunIds(listOf(1L)) }
        verify(exactly = 1) { pipelineRunRepository.findByIds(listOf(1L)) }
        verify(exactly = 0) { pilotEventRepository.findByRunId(any()) }
    }

    /** 한계 문구는 역분개와 무관하다. 정정 수단이 생겨도 수기 입력이라는 사실은 그대로다. */
    @Test
    fun `역분개가 있어도 한계 목록은 그대로다`() {
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(1L)
        every { pilotEventRepository.findByRunIds(any()) } returns listOf(
            event(1L, ShortsPilotEventType.OPERATOR_REVENUE_LOGGED, amountKrw = 300_000, id = 71L),
            reversal(1L, target = 71L, id = 72L),
        )
        every { pipelineRunRepository.findByIds(any()) } returns listOf(run(1L))

        assertTrue(
            ShortsPilotLimitation.REVENUE_AND_COST_ARE_OPERATOR_REPORTED in useCase.report().limitations,
        )
    }

    /**
     * 응답 어디에도 식별자가 실리면 안 된다. 반복 여부는 참·거짓 하나이며, 그것만으로는
     * 어느 실행들이 같은 고객인지 되짚을 수 없어야 한다.
     */
    @Test
    fun `응답 DTO 에 고객 식별자 필드가 없다`() {
        val rowFields = ShortsPilotRunRow::class.members.map { it.name }.toSet()
        val summaryFields = ShortsPilotReportSummary::class.members.map { it.name }.toSet()

        for (forbidden in listOf("userId", "customerId", "email", "workspaceId")) {
            assertFalse(forbidden in rowFields, "행에 식별자가 새어 나왔다: $forbidden")
            assertFalse(forbidden in summaryFields, "요약에 식별자가 새어 나왔다: $forbidden")
        }
    }
}
