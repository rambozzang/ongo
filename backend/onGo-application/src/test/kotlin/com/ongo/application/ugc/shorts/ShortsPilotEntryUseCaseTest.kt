package com.ongo.application.ugc.shorts

import com.ongo.application.ugc.shorts.dto.ShortsPilotEntryRow
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.shorts.ShortsPilotActorType
import com.ongo.domain.ugc.shorts.ShortsPilotEvent
import com.ongo.domain.ugc.shorts.ShortsPilotEventRepository
import com.ongo.domain.ugc.shorts.ShortsPilotEventType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 수기 기록의 열람과 역분개.
 *
 * 매출·외부원가·시간은 사람이 손으로 적는다. 300,000 을 3,000,000 으로 잘못 치면
 * 지금까지 고칠 방법이 전혀 없었다 — update/delete 가 없고 음수도 막힌다. 그래서 오입력이
 * 영구히 "그럴듯한 숫자"로 합계에 참여했다.
 *
 * 여기서 고정하는 것은 세 가지다.
 *  1. **원본을 지우지 않는다** — 취소해도 목록에 남고 isReversed 로만 표시된다
 *  2. **취소 대상이 좁다** — 취소의 취소, 자동 이벤트, 남의 실행은 전부 거절
 *  3. **동시 요청에도 취소는 하나** — 판정을 DB 제약에 맡긴다
 */
class ShortsPilotEntryUseCaseTest {

    private val pilotEventRepository = mockk<ShortsPilotEventRepository>()
    private val useCase = ShortsPilotEntryUseCase(pilotEventRepository)

    private val runId = 7L
    private val actorUserId = 3L
    private val t0 = Instant.parse("2026-08-20T01:00:00Z")

    private fun entry(
        id: Long,
        type: ShortsPilotEventType,
        amountKrw: Long? = null,
        minutes: Int? = null,
        reverses: Long? = null,
        run: Long = runId,
    ) = ShortsPilotEvent(
        id = id,
        runId = run,
        eventType = type,
        actorType = ShortsPilotActorType.ADMIN,
        actorId = actorUserId,
        amountKrw = amountKrw,
        operatorMinutes = minutes,
        reversesEventId = reverses,
        createdAt = t0,
    )

    private fun revenue(id: Long, amountKrw: Long = 300_000) =
        entry(id, ShortsPilotEventType.OPERATOR_REVENUE_LOGGED, amountKrw = amountKrw)

    private fun time(id: Long, minutes: Int = 90) =
        entry(id, ShortsPilotEventType.OPERATOR_TIME_LOGGED, minutes = minutes)

    private fun reversal(id: Long, target: Long) =
        entry(id, ShortsPilotEventType.OPERATOR_ENTRY_REVERSED, reverses = target)

    /* ---- 목록 ---- */

    @Test
    fun `수기 기록만 목록에 담고 자동 이벤트는 빼낸다`() {
        every { pilotEventRepository.findByRunId(runId) } returns listOf(
            entry(1L, ShortsPilotEventType.PILOT_ENROLLED),
            entry(2L, ShortsPilotEventType.STAGE_RERUN),
            entry(3L, ShortsPilotEventType.RENDER_ATTEMPT_FAILED),
            revenue(4L),
            time(5L),
        )

        val entries = useCase.entries(runId).entries

        assertEquals(listOf(4L, 5L), entries.map { it.entryId })
    }

    /** 취소해도 행은 남아야 한다. 사라지면 무엇을 잘못 적었었는지 확인할 수 없다. */
    @Test
    fun `취소된 기록도 목록에 남고 표시만 바뀐다`() {
        every { pilotEventRepository.findByRunId(runId) } returns listOf(
            revenue(4L),
            reversal(9L, target = 4L),
        )

        val row = useCase.entries(runId).entries.single()

        assertEquals(4L, row.entryId)
        assertTrue(row.isReversed)
        // 금액도 그대로 보여야 무엇이 취소됐는지 알 수 있다.
        assertEquals(300_000L, row.amountKrw)
    }

    @Test
    fun `취소 이벤트 자체는 목록에 나타나지 않는다`() {
        every { pilotEventRepository.findByRunId(runId) } returns listOf(
            revenue(4L),
            reversal(9L, target = 4L),
        )

        assertTrue(useCase.entries(runId).entries.none { it.entryId == 9L })
    }

    @Test
    fun `금액 기록과 시간 기록이 서로의 칸을 침범하지 않는다`() {
        every { pilotEventRepository.findByRunId(runId) } returns listOf(revenue(4L), time(5L))

        val byId = useCase.entries(runId).entries.associateBy { it.entryId }

        assertEquals(300_000L, byId.getValue(4L).amountKrw)
        assertNull(byId.getValue(4L).operatorMinutes)
        assertEquals(90, byId.getValue(5L).operatorMinutes)
        assertNull(byId.getValue(5L).amountKrw)
    }

    /** 목록 조회에 질의를 더하면 화면이 커질수록 조용히 자란다. */
    @Test
    fun `목록은 한 번의 조회로 만든다`() {
        every { pilotEventRepository.findByRunId(runId) } returns listOf(revenue(4L))

        useCase.entries(runId)

        verify(exactly = 1) { pilotEventRepository.findByRunId(runId) }
    }

    /**
     * 응답에 누가 적었는지는 담지 않는다. 취소 판단에 필요한 것은 무엇이 언제 얼마로
     * 적혔는가이지 누가 적었는가가 아니다.
     */
    @Test
    fun `기록 DTO 에 식별자 필드가 없다`() {
        val fields = ShortsPilotEntryRow::class.members.map { it.name }.toSet()

        for (forbidden in listOf("actorId", "userId", "email", "runId")) {
            assertFalse(forbidden in fields, "기록 응답에 식별자가 새어 나왔다: $forbidden")
        }
    }

    /* ---- 취소 ---- */

    @Test
    fun `수기 기록을 취소하면 원본을 가리키는 취소 행을 만든다`() {
        every { pilotEventRepository.findByRunId(runId) } returns listOf(revenue(4L))
        val saved = slot<ShortsPilotEvent>()
        every { pilotEventRepository.insertReversalIfAbsent(capture(saved)) } returns true

        val result = useCase.reverse(actorUserId, runId, entryId = 4L)

        assertEquals(4L, result.entryId)
        assertFalse(result.alreadyReversed)
        assertEquals(ShortsPilotEventType.OPERATOR_ENTRY_REVERSED, saved.captured.eventType)
        assertEquals(4L, saved.captured.reversesEventId)
        assertEquals(runId, saved.captured.runId)
        assertEquals(actorUserId, saved.captured.actorId)
        // 취소는 "빼라"는 지시일 뿐 새 값이 아니다.
        assertNull(saved.captured.amountKrw)
        assertNull(saved.captured.operatorMinutes)
    }

    /**
     * 역분개는 **전용 경로로만** 만든다.
     *
     * `save` 도 이제 reverses_event_id 를 저장하지만(도메인·영속 매핑 일치), 그 경로로
     * 취소를 넣으면 [ShortsPilotEventRepository.insertReversalIfAbsent] 가 주는 멱등을
     * 잃는다 — 중복 요청이 조용히 0행이 되는 대신 부분 유니크 인덱스 위반으로 터진다.
     * DB 제약이 정합성은 지켜 주지만 그건 정합성이지 API 계약이 아니다.
     */
    @Test
    fun `역분개는 일반 save 경로를 쓰지 않는다`() {
        every { pilotEventRepository.findByRunId(runId) } returns listOf(revenue(4L))
        every { pilotEventRepository.insertReversalIfAbsent(any()) } returns true

        useCase.reverse(actorUserId, runId, entryId = 4L)

        verify(exactly = 1) { pilotEventRepository.insertReversalIfAbsent(any()) }
        verify(exactly = 0) { pilotEventRepository.save(any()) }
    }

    /**
     * 동시 요청 둘이 모두 "아직 취소 안 됨"을 보고 통과하면 같은 원본에 취소 행이 두 개
     * 생긴다. 판정을 DB 부분 유니크 인덱스에 맡기고, 두 번째는 예외 없이 성공으로 받는다.
     */
    @Test
    fun `이미 취소된 기록을 다시 취소해도 성공이고 행을 더 만들지 않는다`() {
        every { pilotEventRepository.findByRunId(runId) } returns listOf(
            revenue(4L),
            reversal(9L, target = 4L),
        )
        every { pilotEventRepository.insertReversalIfAbsent(any()) } returns false

        val result = useCase.reverse(actorUserId, runId, entryId = 4L)

        assertTrue(result.alreadyReversed)
        verify(exactly = 1) { pilotEventRepository.insertReversalIfAbsent(any()) }
    }

    /** 조회 후 삽입이면 경쟁 상태가 그대로 남는다. 존재 확인으로 미리 걸러내면 안 된다. */
    @Test
    fun `취소 여부를 미리 조회해 건너뛰지 않고 항상 삽입을 시도한다`() {
        every { pilotEventRepository.findByRunId(runId) } returns listOf(
            revenue(4L),
            reversal(9L, target = 4L),
        )
        every { pilotEventRepository.insertReversalIfAbsent(any()) } returns false

        useCase.reverse(actorUserId, runId, entryId = 4L)

        verify(exactly = 1) { pilotEventRepository.insertReversalIfAbsent(any()) }
    }

    /* ---- 거절 ---- */

    /** 취소의 취소를 허용하면 합계가 되살아나고, 이 원장은 수정 가능한 장부가 된다. */
    @Test
    fun `취소 이벤트 자체는 취소할 수 없다`() {
        every { pilotEventRepository.findByRunId(runId) } returns listOf(
            revenue(4L),
            reversal(9L, target = 4L),
        )

        val e = assertFailsWith<BusinessException> { useCase.reverse(actorUserId, runId, entryId = 9L) }

        assertEquals("SHORTS_PILOT_ENTRY_NOT_REVERSIBLE", e.code)
        verify(exactly = 0) { pilotEventRepository.insertReversalIfAbsent(any()) }
    }

    /** 재실행·렌더 실패는 사람이 적은 값이 아니라 일어난 사실이다. */
    @Test
    fun `자동 이벤트는 취소할 수 없다`() {
        every { pilotEventRepository.findByRunId(runId) } returns listOf(
            entry(2L, ShortsPilotEventType.STAGE_RERUN),
            entry(3L, ShortsPilotEventType.RENDER_ATTEMPT_FAILED),
            entry(1L, ShortsPilotEventType.PILOT_ENROLLED),
        )

        for (id in listOf(1L, 2L, 3L)) {
            assertFailsWith<BusinessException>("id=$id 는 취소되면 안 된다") {
                useCase.reverse(actorUserId, runId, entryId = id)
            }
        }
        verify(exactly = 0) { pilotEventRepository.insertReversalIfAbsent(any()) }
    }

    /**
     * 다른 실행의 이벤트 id 로는 취소되지 않는다. 이 실행의 목록 안에서만 찾으므로
     * 남의 이벤트가 존재하는지도 응답으로 새어 나가지 않는다 — 없는 id 와 같은 응답이다.
     */
    @Test
    fun `다른 실행의 기록은 취소할 수 없다`() {
        every { pilotEventRepository.findByRunId(runId) } returns listOf(revenue(4L))

        assertFailsWith<NotFoundException> { useCase.reverse(actorUserId, runId, entryId = 999L) }
        verify(exactly = 0) { pilotEventRepository.insertReversalIfAbsent(any()) }
    }

    @Test
    fun `없는 기록은 취소할 수 없다`() {
        every { pilotEventRepository.findByRunId(runId) } returns emptyList()

        assertFailsWith<NotFoundException> { useCase.reverse(actorUserId, runId, entryId = 4L) }
    }
}
