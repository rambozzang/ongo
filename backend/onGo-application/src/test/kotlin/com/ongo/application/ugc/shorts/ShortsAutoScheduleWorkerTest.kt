package com.ongo.application.ugc.shorts

import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.PipelineRunStatus
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.ShortsClip
import com.ongo.domain.ugc.shorts.ShortsClipRepository
import com.ongo.domain.ugc.shorts.ShortsRenderJob
import com.ongo.domain.ugc.shorts.ShortsRenderJobRepository
import com.ongo.domain.ugc.shorts.ShortsRenderJobStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortsAutoScheduleWorkerTest {

    private val runRepository = mockk<PipelineRunRepository>()
    private val clipRepository = mockk<ShortsClipRepository>()
    private val renderJobRepository = mockk<ShortsRenderJobRepository>()
    private val renderUseCase = mockk<ShortsRenderUseCase>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val run = PipelineRun(
        id = 1L,
        workspaceId = 10L,
        userId = 7L,
        sourceVideoId = 99L,
        autoSchedule = true,
        autoScheduleStartAt = Instant.parse("2026-09-01T00:00:00Z"),
        autoScheduleIntervalHours = 2,
        autoSchedulePlatforms = listOf("YOUTUBE#101"),
        status = PipelineRunStatus.AWAITING_SCHEDULE,
    )

    private val clip = ShortsClip(
        id = 11L,
        runId = run.id,
        seq = 1,
        startMs = 0,
        endMs = 30_000,
        title = "쇼츠",
        caption = "캡션",
        renderSpec = "{}",
        status = ClipStatus.RENDER_READY,
    )

    private fun worker() = ShortsAutoScheduleWorker(
        pipelineRunRepository = runRepository,
        shortsClipRepository = clipRepository,
        renderJobRepository = renderJobRepository,
        renderUseCase = renderUseCase,
        eventPublisher = eventPublisher,
    )

    private fun common() {
        every { runRepository.findById(run.id) } returns run
        every { runRepository.update(any()) } answers { firstArg() }
        every { clipRepository.update(any()) } answers { firstArg() }
    }

    @Test
    fun `렌더 job이 완료됐지만 클립 연결이 없으면 videoId로 연결을 복구하고 예약 이벤트를 발행한다`() {
        common()
        val completed = ShortsRenderJob(
            id = "job-1",
            runId = run.id,
            clipId = clip.id,
            status = ShortsRenderJobStatus.COMPLETED,
            videoId = 501L,
        )
        every { clipRepository.findByRunId(run.id) } returnsMany listOf(listOf(clip), listOf(clip.copy(renderedVideoId = 501L)))
        every { renderJobRepository.findByRunAndClip(run.id, clip.id) } returns completed

        worker().process(run)

        val repaired = slot<ShortsClip>()
        verify { clipRepository.update(capture(repaired)) }
        assertEquals(501L, repaired.captured.renderedVideoId)
        assertEquals(ClipStatus.RENDERED, repaired.captured.status)

        val event = slot<ShortsPipelineEvent>()
        verify { eventPublisher.publishEvent(capture(event)) }
        assertEquals(PipelineStage.SCHEDULE, event.captured.fromStage)
        assertEquals(run.autoScheduleStartAt, event.captured.scheduleStartAt)
        assertEquals(run.autoScheduleIntervalHours, event.captured.scheduleIntervalHours)
        assertEquals(run.autoSchedulePlatforms, event.captured.platforms)
    }

    @Test
    fun `완료 job에 videoId가 없으면 실패시키고 재렌더나 예약을 시도하지 않는다`() {
        common()
        every { clipRepository.findByRunId(run.id) } returns listOf(clip)
        every { renderJobRepository.findByRunAndClip(run.id, clip.id) } returns ShortsRenderJob(
            id = "job-1",
            runId = run.id,
            clipId = clip.id,
            status = ShortsRenderJobStatus.COMPLETED,
            videoId = null,
        )

        worker().process(run)

        val updated = slot<PipelineRun>()
        verify { runRepository.update(capture(updated)) }
        assertEquals(PipelineRunStatus.FAILED, updated.captured.status)
        assertTrue(updated.captured.errorMessage.orEmpty().contains("영상 식별자"))
        verify(exactly = 0) { renderUseCase.requestRender(any(), any(), any(), any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `렌더 job이 실패하면 실패 사유를 보존하고 예약을 발행하지 않는다`() {
        common()
        every { clipRepository.findByRunId(run.id) } returns listOf(clip)
        every { renderJobRepository.findByRunAndClip(run.id, clip.id) } returns ShortsRenderJob(
            id = "job-1",
            runId = run.id,
            clipId = clip.id,
            status = ShortsRenderJobStatus.FAILED,
            failureReason = "ffmpeg 실패",
        )

        worker().process(run)

        val updated = slot<PipelineRun>()
        verify { runRepository.update(capture(updated)) }
        assertEquals(PipelineRunStatus.FAILED, updated.captured.status)
        assertEquals("ffmpeg 실패", updated.captured.errorMessage)
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `렌더 job이 없으면 한 번만 렌더를 요청하고 완료 전에는 예약하지 않는다`() {
        common()
        every { clipRepository.findByRunId(run.id) } returns listOf(clip)
        every { renderJobRepository.findByRunAndClip(run.id, clip.id) } returns null
        every { renderUseCase.requestRender(run.userId, run.workspaceId, run.id, clip.id) } returns ShortsRenderJob(
            id = "job-1",
            runId = run.id,
            clipId = clip.id,
            status = ShortsRenderJobStatus.RUNNING,
        )

        worker().process(run)

        verify(exactly = 1) { renderUseCase.requestRender(run.userId, run.workspaceId, run.id, clip.id) }
        verify(exactly = 0) { runRepository.update(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        assertNull(clip.renderedVideoId)
    }
}
