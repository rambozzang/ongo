package com.ongo.application.ugc.shorts

import com.ongo.domain.ugc.shorts.ShortsRenderJob
import com.ongo.domain.ugc.shorts.ShortsRenderJobRepository
import com.ongo.domain.ugc.shorts.ShortsRenderJobStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ShortsRenderJobStateServiceTest {

    private val repository = mockk<ShortsRenderJobRepository>()
    private val service = ShortsRenderJobStateService(repository)

    @Test
    fun `없는 클립은 하나의 queued job으로 생성된다`() {
        every { repository.findByRunAndClip(1, 2) } returns null
        every { repository.saveIfAbsent(any()) } answers { firstArg() }

        val job = service.enqueue(1, 2)

        assertEquals(ShortsRenderJobStatus.QUEUED, job.status)
        assertEquals(1, job.runId)
        assertEquals(2, job.clipId)
        verify(exactly = 1) { repository.saveIfAbsent(any()) }
    }

    @Test
    fun `실패 job 재요청은 새 id 없이 queued로 복구된다`() {
        val failed = job(ShortsRenderJobStatus.FAILED, failureReason = "timeout")
        every { repository.findByRunAndClip(1, 2) } returns failed
        every { repository.update(any()) } answers { firstArg() }

        val retried = service.enqueue(1, 2)

        assertEquals(failed.id, retried.id)
        assertEquals(ShortsRenderJobStatus.QUEUED, retried.status)
        assertEquals(0, retried.attemptCount)
        assertEquals(null, retried.failureReason)
    }

    @Test
    fun `queued 를 running으로 옮길 때 시도 횟수가 증가한다`() {
        val queued = job(ShortsRenderJobStatus.QUEUED)
        every { repository.findById(queued.id) } returns queued
        every { repository.claimQueued(eq(queued.id), any()) } answers {
            queued.copy(status = ShortsRenderJobStatus.RUNNING, attemptCount = queued.attemptCount + 1, progress = 0)
        }

        val running = service.markRunning(queued.id)

        assertEquals(ShortsRenderJobStatus.RUNNING, running.status)
        assertEquals(1, running.attemptCount)
        assertEquals(0, running.progress)
        verify(exactly = 1) { repository.claimQueued(eq(queued.id), any()) }
    }

    @Test
    fun `running 을 completed로 옮기면 progress 100과 video id를 저장한다`() {
        val running = job(ShortsRenderJobStatus.RUNNING)
        every { repository.findById(running.id) } returns running
        every { repository.update(any()) } answers { firstArg() }

        val completed = service.markCompleted(running.id, 77)

        assertEquals(ShortsRenderJobStatus.COMPLETED, completed.status)
        assertEquals(100, completed.progress)
        assertEquals(77, completed.videoId)
    }

    @Test
    fun `running 을 failed로 옮기고 실패 사유를 1000자로 제한한다`() {
        val running = job(ShortsRenderJobStatus.RUNNING)
        every { repository.findById(running.id) } returns running
        every { repository.update(any()) } answers { firstArg() }

        val failed = service.markFailed(running.id, "x".repeat(1500))

        assertEquals(ShortsRenderJobStatus.FAILED, failed.status)
        assertEquals(1000, failed.failureReason?.length)
    }

    @Test
    fun `completed job을 다시 실패 처리하지 않는다`() {
        val completed = job(ShortsRenderJobStatus.COMPLETED)
        every { repository.findById(completed.id) } returns completed

        assertEquals(completed, service.markFailed(completed.id, "late"))
        verify(exactly = 0) { repository.update(any()) }
    }

    private fun job(status: ShortsRenderJobStatus, failureReason: String? = null) =
        ShortsRenderJob(id = "job-1", runId = 1, clipId = 2, status = status, failureReason = failureReason)
}
