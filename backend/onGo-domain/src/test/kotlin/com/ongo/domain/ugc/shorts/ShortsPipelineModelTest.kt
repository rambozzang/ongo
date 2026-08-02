package com.ongo.domain.ugc.shorts

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 2에서 쓰이는 도메인 모델의 copy 기반 상태 전이 규칙을 고정한다.
 * 유스케이스/오케스트레이터는 전부 불변 모델의 copy로 상태를 바꾼다.
 */
class ShortsPipelineModelTest {

    private fun run(
        status: PipelineRunStatus = PipelineRunStatus.FAILED,
        clipCount: Int = 2,
    ) = PipelineRun(
        id = 1,
        workspaceId = 10,
        userId = 1,
        sourceVideoId = 5,
        status = status,
        currentStage = PipelineStage.HOOK,
        transcriptText = "전사 전문",
        clipCount = clipCount,
        errorMessage = "이전 오류",
        version = 3,
    )

    @Test
    fun `재실행 리셋은 상태 필드만 되돌리고 누적 결과와 식별자는 유지한다`() {
        val original = run()
        val reset = original.copy(status = PipelineRunStatus.PENDING, currentStage = null, errorMessage = null)

        assertEquals(PipelineRunStatus.PENDING, reset.status)
        assertNull(reset.currentStage)
        assertNull(reset.errorMessage)
        // 누적 결과와 식별 정보는 그대로다
        assertEquals(original.id, reset.id)
        assertEquals(original.clipCount, reset.clipCount)
        assertEquals(original.transcriptText, reset.transcriptText)
        // 원본은 불변이다
        assertEquals(PipelineRunStatus.FAILED, original.status)
    }

    @Test
    fun `HOOK 재실행 롤백은 클립을 DRAFT로 되돌리고 예약 시각을 지운다`() {
        val clip = ShortsClip(
            id = 11, runId = 1, seq = 1, startMs = 0, endMs = 15000,
            status = ClipStatus.HOOK_SELECTED, scheduledAt = Instant.now(),
        )
        val rolledBack = clip.copy(status = ClipStatus.DRAFT, scheduledAt = null)

        assertEquals(ClipStatus.DRAFT, rolledBack.status)
        assertNull(rolledBack.scheduledAt)
        assertEquals(clip.id, rolledBack.id)
        assertEquals(clip.startMs, rolledBack.startMs)
    }

    @Test
    fun `후킹 선택과 클립 폐기는 서로 다른 클립 상태로 기록된다`() {
        val clip = ShortsClip(id = 11, runId = 1, seq = 1, startMs = 0, endMs = 15000)

        assertEquals(ClipStatus.HOOK_SELECTED, clip.copy(status = ClipStatus.HOOK_SELECTED).status)
        assertEquals(ClipStatus.DISCARDED, clip.copy(status = ClipStatus.DISCARDED).status)
        // 원본은 DRAFT 그대로다
        assertEquals(ClipStatus.DRAFT, clip.status)
    }

    @Test
    fun `후킹 문구 선택 표시는 variant와 text를 유지한 채 selected만 바꾼다`() {
        val hook = ClipHook(id = 21, clipId = 11, variant = HookVariant.A, text = "후킹 문구")
        val selected = hook.copy(selected = true)

        assertTrue(selected.selected)
        assertEquals(HookVariant.A, selected.variant)
        assertEquals("후킹 문구", selected.text)
        assertFalse(hook.selected)
    }

    @Test
    fun `렌더 스펙 반영은 클립을 RENDER_READY로 올린다`() {
        val clip = ShortsClip(
            id = 11, runId = 1, seq = 1, startMs = 0, endMs = 15000,
            status = ClipStatus.HOOK_SELECTED,
        )
        val ready = clip.copy(renderSpec = "{}", status = ClipStatus.RENDER_READY)

        assertEquals(ClipStatus.RENDER_READY, ready.status)
        assertNotNull(ready.renderSpec)
    }

    @Test
    fun `예약 반영은 클립을 SCHEDULED로 올리고 예약 시각을 기록한다`() {
        val at = Instant.parse("2026-03-01T09:00:00Z")
        val clip = ShortsClip(
            id = 11, runId = 1, seq = 1, startMs = 0, endMs = 15000,
            status = ClipStatus.RENDER_READY,
        )
        val scheduled = clip.copy(scheduledAt = at, status = ClipStatus.SCHEDULED)

        assertEquals(ClipStatus.SCHEDULED, scheduled.status)
        assertEquals(at, scheduled.scheduledAt)
    }

    @Test
    fun `단계 실패 기록은 FAILED 상태와 오류 메시지를 담는다`() {
        val stage = RunStage(
            id = 31, runId = 1, stage = PipelineStage.SEGMENT,
            status = RunStageStatus.RUNNING, startedAt = Instant.now(),
        )
        val failed = stage.copy(status = RunStageStatus.FAILED, errorMessage = "AI 장애", completedAt = Instant.now())

        assertEquals(RunStageStatus.FAILED, failed.status)
        assertEquals("AI 장애", failed.errorMessage)
        assertNotNull(failed.completedAt)
        // 원본은 RUNNING 그대로다
        assertEquals(RunStageStatus.RUNNING, stage.status)
    }

    @Test
    fun `후킹 문구 종류는 AI 생성안 A B와 사용자 직접 입력 CUSTOM이다`() {
        assertEquals(listOf(HookVariant.A, HookVariant.B, HookVariant.CUSTOM), HookVariant.entries)
    }
}
