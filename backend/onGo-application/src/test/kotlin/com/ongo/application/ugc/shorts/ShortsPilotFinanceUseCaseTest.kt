package com.ongo.application.ugc.shorts

import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
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
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 운영자가 확인한 매출·외부원가 기록.
 *
 * 이 값들은 청구 시스템 연동이 아니라 사람이 손으로 적은 것이다. append-only 원장이라
 * 잘못 들어간 값을 나중에 지울 수 없으므로, 들어오는 순간에 막는다.
 */
@ExtendWith(MockKExtension::class)
class ShortsPilotFinanceUseCaseTest {

    @MockK lateinit var pipelineRunRepository: PipelineRunRepository
    @MockK lateinit var pilotEventRepository: ShortsPilotEventRepository

    @InjectMockKs lateinit var useCase: ShortsPilotFinanceUseCase

    private val adminId = 9L
    private val runId = 77L

    private fun enrolled() {
        every { pipelineRunRepository.findById(runId) } returns
            PipelineRun(id = runId, workspaceId = 5L, userId = 3L, sourceVideoId = 99L)
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(runId)
    }

    @Test
    fun `매출을 운영자 입력으로 기록한다`() {
        enrolled()
        val recorded = slot<ShortsPilotEvent>()
        every { pilotEventRepository.save(capture(recorded)) } answers { firstArg() }

        useCase.logRevenue(actorUserId = adminId, runId = runId, amountKrw = 50_000)

        assertEquals(ShortsPilotEventType.OPERATOR_REVENUE_LOGGED, recorded.captured.eventType)
        assertEquals(ShortsPilotActorType.ADMIN, recorded.captured.actorType)
        assertEquals(adminId, recorded.captured.actorId)
        assertEquals(50_000L, recorded.captured.amountKrw)
        // 금액 이벤트에 분이 붙으면 시간 합계가 오염된다.
        assertEquals(null, recorded.captured.operatorMinutes)
    }

    @Test
    fun `외부 인프라 원가를 별도 이벤트로 기록한다`() {
        enrolled()
        val recorded = slot<ShortsPilotEvent>()
        every { pilotEventRepository.save(capture(recorded)) } answers { firstArg() }

        useCase.logExternalCost(actorUserId = adminId, runId = runId, amountKrw = 1_200)

        assertEquals(ShortsPilotEventType.OPERATOR_EXTERNAL_COST_LOGGED, recorded.captured.eventType)
        assertEquals(1_200L, recorded.captured.amountKrw)
    }

    /* 분할 청구나 추가 비용은 행을 더 쌓아 표현한다. 원장을 고치지 않는다. */
    @Test
    fun `같은 실행에 여러 번 쌓을 수 있다`() {
        enrolled()
        val recorded = mutableListOf<ShortsPilotEvent>()
        every { pilotEventRepository.save(capture(recorded)) } answers { firstArg() }

        useCase.logRevenue(actorUserId = adminId, runId = runId, amountKrw = 30_000)
        useCase.logRevenue(actorUserId = adminId, runId = runId, amountKrw = 20_000)

        assertEquals(listOf(30_000L, 20_000L), recorded.map { it.amountKrw })
    }

    /*
     * 0 은 "무상 제공"과 "기록을 깜빡했다"를 구분하지 못한다. 무상 건은 아예 적지 않고
     * 리포트에서 미기록으로 남는 편이 정직하다. 상한은 자릿수 오타 방어다.
     */
    @Test
    fun `범위를 벗어난 금액은 실행을 조회하기도 전에 거절한다`() {
        listOf(0L, -1L, 100_000_001L, Long.MAX_VALUE).forEach { amount ->
            val revenueEx = assertFailsWith<BusinessException>("$amount 매출이 통과했다") {
                useCase.logRevenue(actorUserId = adminId, runId = runId, amountKrw = amount)
            }
            assertEquals("SHORTS_PILOT_INVALID_AMOUNT", revenueEx.code)

            val costEx = assertFailsWith<BusinessException>("$amount 원가가 통과했다") {
                useCase.logExternalCost(actorUserId = adminId, runId = runId, amountKrw = amount)
            }
            assertEquals("SHORTS_PILOT_INVALID_AMOUNT", costEx.code)
        }

        verify(exactly = 0) { pipelineRunRepository.findById(any()) }
        verify(exactly = 0) { pilotEventRepository.save(any()) }
    }

    @Test
    fun `경계값 1원과 1억원은 받는다`() {
        enrolled()
        val recorded = mutableListOf<ShortsPilotEvent>()
        every { pilotEventRepository.save(capture(recorded)) } answers { firstArg() }

        useCase.logRevenue(actorUserId = adminId, runId = runId, amountKrw = 1)
        useCase.logRevenue(actorUserId = adminId, runId = runId, amountKrw = 100_000_000)

        assertEquals(listOf(1L, 100_000_000L), recorded.map { it.amountKrw })
    }

    /*
     * 리포트는 등록된 실행만 집계한다. 미등록에 허용하면 어디에도 나타나지 않는 행이
     * 쌓이고 운영자는 입력이 반영된 줄 안다.
     */
    @Test
    fun `파일럿 미등록 실행은 거절하고 이벤트를 남기지 않는다`() {
        every { pipelineRunRepository.findById(runId) } returns
            PipelineRun(id = runId, workspaceId = 5L, userId = 3L, sourceVideoId = 99L)
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(999L)

        val ex = assertFailsWith<BusinessException> {
            useCase.logRevenue(actorUserId = adminId, runId = runId, amountKrw = 10_000)
        }

        assertEquals("SHORTS_PILOT_RUN_NOT_ENROLLED", ex.code)
        verify(exactly = 0) { pilotEventRepository.save(any()) }
    }

    @Test
    fun `없는 실행은 NotFound 로 거절하고 이벤트를 남기지 않는다`() {
        every { pipelineRunRepository.findById(runId) } returns null

        assertFailsWith<NotFoundException> {
            useCase.logExternalCost(actorUserId = adminId, runId = runId, amountKrw = 10_000)
        }

        verify(exactly = 0) { pilotEventRepository.save(any()) }
    }
}
