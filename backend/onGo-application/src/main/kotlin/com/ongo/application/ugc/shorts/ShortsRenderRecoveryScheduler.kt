package com.ongo.application.ugc.shorts

import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.ShortsRenderJobRepository
import com.ongo.domain.ugc.shorts.ShortsRenderJobStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/** 서버 재기동/프로세스 중단으로 잃은 렌더 제출을 영속 job에서 복구한다. */
@Component
class ShortsRenderRecoveryScheduler(
    private val jobRepository: ShortsRenderJobRepository,
    private val runRepository: PipelineRunRepository,
    private val renderUseCase: ShortsRenderUseCase,
    @Value("\${shorts.render.recovery-stale-after-ms:3600000}")
    private val staleAfterMs: Long,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${shorts.render.recovery-delay-ms:15000}")
    @Transactional
    fun recover() {
        val now = Instant.now()
        val queued = jobRepository.findByStatus(ShortsRenderJobStatus.QUEUED, BATCH_SIZE)
        val staleRunning = jobRepository.findByStatus(ShortsRenderJobStatus.RUNNING, BATCH_SIZE)
            .filter { it.startedAt?.let { started -> Duration.between(started, now).toMillis() >= staleAfterMs } == true }

        (queued + staleRunning).distinctBy { it.id }.forEach { job ->
            if (renderUseCase.isActiveInThisProcess(job.id)) return@forEach
            val current = jobRepository.findById(job.id) ?: return@forEach
            if (current.status == ShortsRenderJobStatus.RUNNING) {
                jobRepository.update(
                    current.copy(
                        status = ShortsRenderJobStatus.QUEUED,
                        progress = null,
                        failureReason = "서버 재기동으로 렌더 작업을 복구했습니다",
                        startedAt = null,
                        completedAt = null,
                        updatedAt = now,
                    ),
                )
            }
            val run = runRepository.findById(job.runId) ?: return@forEach
            runCatching { renderUseCase.requestRender(run.userId, run.workspaceId, run.id, job.clipId) }
                .onFailure { error -> log.warn("렌더 job 복구 실패: jobId={}", job.id, error) }
        }
    }

    companion object {
        private const val BATCH_SIZE = 50
    }
}
