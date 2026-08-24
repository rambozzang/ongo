package com.ongo.application.ugc.shorts

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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 파일럿 코호트 등록.
 *
 * "돈을 받고 만든 실행"은 데이터로 구분되지 않는다 — 결제 테이블과 파이프라인 실행 사이에
 * 연결이 없기 때문이다. 5~10명 규모에서는 운영자가 명시적으로 표시하는 편이 정확하다.
 */
@ExtendWith(MockKExtension::class)
class ShortsPilotEnrollmentUseCaseTest {

    @MockK lateinit var pipelineRunRepository: PipelineRunRepository
    @MockK lateinit var pilotEventRepository: ShortsPilotEventRepository

    @InjectMockKs lateinit var useCase: ShortsPilotEnrollmentUseCase

    private val adminId = 9L
    private val runId = 77L

    private fun existingRun() =
        PipelineRun(id = runId, workspaceId = 5L, userId = 3L, sourceVideoId = 99L)

    @Test
    fun `운영자가 실행을 파일럿 코호트에 등록한다`() {
        every { pipelineRunRepository.findById(runId) } returns existingRun()
        val recorded = slot<ShortsPilotEvent>()
        // true = 이번 호출이 행을 만들었다.
        every { pilotEventRepository.insertEnrollmentIfAbsent(capture(recorded)) } returns true

        val result = useCase.enroll(actorUserId = adminId, runId = runId)

        assertFalse(result.alreadyEnrolled)
        assertEquals(runId, result.runId)
        assertEquals(ShortsPilotEventType.PILOT_ENROLLED, recorded.captured.eventType)
        assertEquals(ShortsPilotActorType.ADMIN, recorded.captured.actorType)
        // 누가 코호트를 정했는지는 남고, 고객 식별 정보는 남지 않는다.
        assertEquals(adminId, recorded.captured.actorId)
        assertEquals(null, recorded.captured.attemptNo)
    }

    /*
     * 운영자가 목록을 훑으며 다시 누르는 일은 흔하다. 그때 오류를 띄우면 "내가 뭘 잘못했나"를
     * 확인하느라 시간을 쓴다. 코호트에 들어 있다는 결과는 어느 쪽이든 같다.
     */
    @Test
    fun `순차 재등록도 성공으로 처리한다`() {
        every { pipelineRunRepository.findById(runId) } returns existingRun()
        // 첫 호출만 행을 만든다. 실제 DB 의 ON CONFLICT DO NOTHING 이 돌려주는 순서다.
        var calls = 0
        every { pilotEventRepository.insertEnrollmentIfAbsent(any()) } answers { calls++ == 0 }

        assertFalse(useCase.enroll(actorUserId = adminId, runId = runId).alreadyEnrolled)
        val second = useCase.enroll(actorUserId = adminId, runId = runId)

        assertTrue(second.alreadyEnrolled)
        assertEquals(runId, second.runId)
    }

    /**
     * 동시 요청 경쟁의 계약.
     *
     * 조회 후 삽입이던 시절에는 둘 다 "아직 없다"를 보고 통과했고, 두 번째 INSERT 가
     * 부분 유니크 인덱스에 걸려 500 으로 새어 나갔다. DB 는 중복 행을 막았지만 운영자는
     * 원인 없는 서버 오류를 봤다.
     *
     * 이제 저장소가 충돌을 **예외가 아니라 false** 로 알린다. 여기서는 그 계약이
     * 정상 응답으로 이어지는지만 고정한다 — 실제 동시 INSERT 는 Docker 가 필요해
     * 이 테스트가 그것을 대신한다고 주장하지 않는다.
     */
    @Test
    fun `경쟁에서 진 호출도 예외 없이 이미 등록됨으로 응답한다`() {
        every { pipelineRunRepository.findById(runId) } returns existingRun()
        // 원자 삽입이 0행을 반환한 상황(다른 요청이 먼저 넣음).
        every { pilotEventRepository.insertEnrollmentIfAbsent(any()) } returns false

        val result = useCase.enroll(actorUserId = adminId, runId = runId)

        assertTrue(result.alreadyEnrolled)
        assertEquals(runId, result.runId)
        // 누적용 append 경로로 새지 않는다 — 그러면 등록이 여러 행이 된다.
        verify(exactly = 0) { pilotEventRepository.save(any()) }
    }

    /* 없는 실행을 코호트에 넣으면 파일럿 인원이 실제보다 많아 보인다. */
    @Test
    fun `없는 실행은 NotFound 로 거절하고 이벤트를 남기지 않는다`() {
        every { pipelineRunRepository.findById(runId) } returns null

        assertFailsWith<NotFoundException> { useCase.enroll(actorUserId = adminId, runId = runId) }

        verify(exactly = 0) { pilotEventRepository.insertEnrollmentIfAbsent(any()) }
        verify(exactly = 0) { pilotEventRepository.save(any()) }
    }
}
