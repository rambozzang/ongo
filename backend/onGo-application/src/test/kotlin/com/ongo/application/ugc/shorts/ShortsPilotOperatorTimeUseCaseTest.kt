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
 * 운영자 투입 시간 입력.
 *
 * 이 값은 유일하게 사람이 손으로 넣는 측정치다. 잘못 들어간 값을 나중에 걸러낼 방법이
 * 없으므로(이벤트는 append-only) 들어오는 순간에 막는다.
 */
@ExtendWith(MockKExtension::class)
class ShortsPilotOperatorTimeUseCaseTest {

    @MockK lateinit var pipelineRunRepository: PipelineRunRepository
    @MockK lateinit var pilotEventRepository: ShortsPilotEventRepository

    @InjectMockKs lateinit var useCase: ShortsPilotOperatorTimeUseCase

    private val adminId = 9L
    private val runId = 77L

    private fun existingRun() = PipelineRun(id = runId, workspaceId = 5L, userId = 3L, sourceVideoId = 99L)

    private fun enrolled() {
        every { pipelineRunRepository.findById(runId) } returns existingRun()
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(runId)
    }

    @Test
    fun `등록된 실행에 투입 시간을 기록한다`() {
        enrolled()
        val recorded = slot<ShortsPilotEvent>()
        every { pilotEventRepository.save(capture(recorded)) } answers { firstArg() }

        useCase.log(actorUserId = adminId, runId = runId, minutes = 45)

        assertEquals(ShortsPilotEventType.OPERATOR_TIME_LOGGED, recorded.captured.eventType)
        assertEquals(ShortsPilotActorType.ADMIN, recorded.captured.actorType)
        assertEquals(adminId, recorded.captured.actorId)
        assertEquals(45, recorded.captured.operatorMinutes)
        // 회차는 렌더 실패 전용이다.
        assertEquals(null, recorded.captured.attemptNo)
    }

    /*
     * 보고는 등록된 실행만 집계한다. 미등록 실행에 기록을 허용하면 어디에도 나타나지 않는
     * 행이 쌓이고, 운영자는 입력이 반영된 줄 안다.
     */
    @Test
    fun `파일럿 미등록 실행은 거절하고 이벤트를 남기지 않는다`() {
        every { pipelineRunRepository.findById(runId) } returns existingRun()
        every { pilotEventRepository.findEnrolledRunIds() } returns listOf(999L)

        val ex = assertFailsWith<BusinessException> {
            useCase.log(actorUserId = adminId, runId = runId, minutes = 30)
        }

        assertEquals("SHORTS_PILOT_RUN_NOT_ENROLLED", ex.code)
        verify(exactly = 0) { pilotEventRepository.save(any()) }
    }

    @Test
    fun `없는 실행은 NotFound 로 거절하고 이벤트를 남기지 않는다`() {
        every { pipelineRunRepository.findById(runId) } returns null

        assertFailsWith<NotFoundException> {
            useCase.log(actorUserId = adminId, runId = runId, minutes = 30)
        }

        verify(exactly = 0) { pilotEventRepository.save(any()) }
    }

    /*
     * 0 은 "안 썼다"와 "기록을 깜빡했다"를 구분하지 못해 합계를 오염시킨다.
     * 1440 초과는 대개 오타이며, 하루를 넘긴 작업은 나눠 적는 편이 정확하다.
     */
    @Test
    fun `범위를 벗어난 분은 실행을 조회하기도 전에 거절한다`() {
        listOf(0, -1, 1441, Int.MAX_VALUE).forEach { minutes ->
            val ex = assertFailsWith<BusinessException>("$minutes 분이 통과했다") {
                useCase.log(actorUserId = adminId, runId = runId, minutes = minutes)
            }
            assertEquals("SHORTS_PILOT_INVALID_OPERATOR_MINUTES", ex.code)
        }

        verify(exactly = 0) { pipelineRunRepository.findById(any()) }
        verify(exactly = 0) { pilotEventRepository.save(any()) }
    }

    @Test
    fun `경계값 1분과 1440분은 받는다`() {
        enrolled()
        val recorded = mutableListOf<ShortsPilotEvent>()
        every { pilotEventRepository.save(capture(recorded)) } answers { firstArg() }

        useCase.log(actorUserId = adminId, runId = runId, minutes = 1)
        useCase.log(actorUserId = adminId, runId = runId, minutes = 1440)

        assertEquals(listOf(1, 1440), recorded.map { it.operatorMinutes })
    }
}
