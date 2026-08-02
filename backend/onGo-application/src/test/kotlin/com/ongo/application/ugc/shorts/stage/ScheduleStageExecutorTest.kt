package com.ongo.application.ugc.shorts.stage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.ShortsClip
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ScheduleStageExecutor — 예약 파라미터로 클립별 scheduledAt을 계산하는 규칙 검증.
 */
class ScheduleStageExecutorTest {

    private val executor = ScheduleStageExecutor()
    private val mapper = jacksonObjectMapper()

    private val startAt: Instant = Instant.parse("2026-03-01T09:00:00Z")

    private fun clip(id: Long, seq: Int, status: ClipStatus = ClipStatus.RENDER_READY) =
        ShortsClip(id = id, runId = 1, seq = seq, startMs = (seq - 1) * 15000L, endMs = seq * 15000L, status = status)

    @Test
    fun `예약 파라미터가 없으면 SHORTS_RUN_INVALID_STATE`() {
        val ex = assertFailsWith<BusinessException> {
            executor.execute(stageContext(clips = listOf(clip(11, 1)), schedule = null))
        }
        assertEquals("SHORTS_RUN_INVALID_STATE", ex.code)
    }

    @Test
    fun `예약 대상 클립이 없으면 SHORTS_RUN_INVALID_STATE`() {
        val ex = assertFailsWith<BusinessException> {
            executor.execute(
                stageContext(
                    clips = listOf(clip(11, 1, ClipStatus.DISCARDED)),
                    schedule = ScheduleParams(startAt, 6, listOf("YOUTUBE")),
                ),
            )
        }
        assertEquals("SHORTS_RUN_INVALID_STATE", ex.code)
    }

    @Test
    fun `클립별 예약 시각은 startAt에 순번 곱한 간격을 더한 값이다`() {
        val clips = listOf(clip(11, 1), clip(12, 2), clip(13, 3))

        val output = executor.execute(
            stageContext(clips = clips, schedule = ScheduleParams(startAt, 6, listOf("YOUTUBE"))),
        )

        assertEquals(
            mapOf(
                11L to startAt,
                12L to startAt.plusSeconds(6 * 3600L),
                13L to startAt.plusSeconds(12 * 3600L),
            ),
            output.scheduledAts,
        )
    }

    @Test
    fun `DISCARDED 클립은 예약에서 빠지고 나머지가 seq 순으로 번호를 받는다`() {
        // 가울데 클립이 폐기되면 마지막 클립이 두 번째 슬롯을 받는다
        val clips = listOf(clip(11, 1), clip(12, 2, ClipStatus.DISCARDED), clip(13, 3))

        val output = executor.execute(
            stageContext(clips = clips, schedule = ScheduleParams(startAt, 6, emptyList())),
        )

        assertEquals(
            mapOf(11L to startAt, 13L to startAt.plusSeconds(6 * 3600L)),
            output.scheduledAts,
        )
    }

    @Test
    fun `출력 스냅샷에 예약 파라미터와 클립별 시각이 기록된다`() {
        val output = executor.execute(
            stageContext(
                clips = listOf(clip(11, 1)),
                schedule = ScheduleParams(startAt, 6, listOf("YOUTUBE", "TIKTOK")),
            ),
        )

        val snapshot = mapper.readTree(output.outputSnapshot)
        assertEquals(startAt.toString(), snapshot.path("startAt").asText())
        assertEquals(6, snapshot.path("intervalHours").asInt())
        assertEquals(listOf("YOUTUBE", "TIKTOK"), snapshot.path("platforms").map { it.asText() })
        assertEquals(11, snapshot.path("clips")[0].path("clipId").asLong())
        assertEquals(startAt.toString(), snapshot.path("clips")[0].path("scheduledAt").asText())
    }
}
