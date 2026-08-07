package com.ongo.application.ugc.shorts

import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.shorts.ShortsRenderJob
import com.ongo.domain.ugc.shorts.ShortsRenderJobRepository
import com.ongo.domain.ugc.shorts.ShortsRenderJobStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * 렌더 job의 허용된 상태 전이와 재시도 정책을 한 곳에서 관리한다.
 *
 * POST 재전송은 새 id를 만들지 않는다. 완료 job은 그대로 반환하고, 실패 job만 같은
 * id로 QUEUED에 되돌려 폴링 클라이언트가 id를 바꾸지 않도록 한다.
 */
@Service
class ShortsRenderJobStateService(
    private val repository: ShortsRenderJobRepository,
) {

    @Transactional
    fun enqueue(runId: Long, clipId: Long): ShortsRenderJob {
        val existing = repository.findByRunAndClip(runId, clipId)
        if (existing == null) {
            return repository.saveIfAbsent(
                ShortsRenderJob(
                    id = UUID.randomUUID().toString(),
                    runId = runId,
                    clipId = clipId,
                    status = ShortsRenderJobStatus.QUEUED,
                ),
            )
        }

        return if (existing.status == ShortsRenderJobStatus.FAILED) {
            repository.update(
                existing.copy(
                    status = ShortsRenderJobStatus.QUEUED,
                    progress = null,
                    failureReason = null,
                    videoId = null,
                    startedAt = null,
                    completedAt = null,
                    updatedAt = Instant.now(),
                ),
            )
        } else {
            existing
        }
    }

    @Transactional
    fun markRunning(jobId: String): ShortsRenderJob {
        val current = find(jobId)
        return when (current.status) {
            ShortsRenderJobStatus.QUEUED -> repository.claimQueued(jobId, Instant.now()) ?: find(jobId)
            ShortsRenderJobStatus.RUNNING,
            ShortsRenderJobStatus.COMPLETED,
            -> current
            ShortsRenderJobStatus.FAILED -> current
        }
    }

    @Transactional
    fun markCompleted(jobId: String, videoId: Long): ShortsRenderJob {
        val current = find(jobId)
        if (current.status == ShortsRenderJobStatus.COMPLETED) return current
        check(current.status == ShortsRenderJobStatus.RUNNING) {
            "실행 중이 아닌 렌더 job은 완료 처리할 수 없습니다: $jobId/${current.status}"
        }
        val now = Instant.now()
        return repository.update(
            current.copy(
                status = ShortsRenderJobStatus.COMPLETED,
                progress = 100,
                videoId = videoId,
                failureReason = null,
                completedAt = now,
                updatedAt = now,
            ),
        )
    }

    @Transactional
    fun markFailed(jobId: String, reason: String): ShortsRenderJob {
        val current = find(jobId)
        if (current.status == ShortsRenderJobStatus.COMPLETED) return current
        check(current.status == ShortsRenderJobStatus.RUNNING || current.status == ShortsRenderJobStatus.QUEUED) {
            "종료된 렌더 job은 실패 처리할 수 없습니다: $jobId/${current.status}"
        }
        return repository.update(
            current.copy(
                status = ShortsRenderJobStatus.FAILED,
                failureReason = reason.take(MAX_FAILURE_REASON_LENGTH),
                updatedAt = Instant.now(),
            ),
        )
    }

    fun find(jobId: String): ShortsRenderJob =
        repository.findById(jobId) ?: throw NotFoundException("렌더 작업", jobId)

    companion object {
        private const val MAX_FAILURE_REASON_LENGTH = 1000
    }
}
