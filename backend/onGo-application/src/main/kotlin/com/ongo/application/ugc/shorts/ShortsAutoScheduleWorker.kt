package com.ongo.application.ugc.shorts

import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.PipelineRunStatus
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.ShortsClipRepository
import com.ongo.domain.ugc.shorts.ShortsRenderJobRepository
import com.ongo.domain.ugc.shorts.ShortsRenderJobStatus
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Compose 원클릭 쇼츠를 서버에서 이어서 처리한다.
 *
 * 브라우저가 AWAITING_SCHEDULE을 기다리거나 클립별 렌더를 시작할 필요가 없다.
 * 워커는 영속 상태만 읽으므로 탭을 닫거나 API 서버가 재기동되어도 같은 실행을
 * 다시 발견할 수 있다. (게시 예약 이벤트는 렌더가 모두 끝난 뒤 한 번만 발행한다.)
 */
@Component
class ShortsAutoScheduleWorker(
    private val pipelineRunRepository: PipelineRunRepository,
    private val shortsClipRepository: ShortsClipRepository,
    private val renderJobRepository: ShortsRenderJobRepository,
    private val renderUseCase: ShortsRenderUseCase,
    private val eventPublisher: ApplicationEventPublisher,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${shorts.auto-schedule.delay-ms:5000}")
    @Transactional
    fun processQueue() {
        pipelineRunRepository.findByStatus(PipelineRunStatus.AWAITING_SCHEDULE, BATCH_SIZE)
            .filter { it.autoSchedule }
            .forEach { run ->
                runCatching { process(run) }
                    .onFailure { error ->
                        // 낙관적 락 충돌은 다른 워커가 정상적으로 가져간 경우일 수 있다.
                        // 다음 tick에서 영속 상태를 다시 읽는다.
                        log.warn("자동 쇼츠 워커가 다음 실행을 처리하지 못했습니다: runId={}", run.id, error)
                    }
            }
    }

    @Transactional
    fun process(run: PipelineRun) {
        val clips = shortsClipRepository.findByRunId(run.id)
            .filter { it.status != ClipStatus.DISCARDED }
        if (clips.isEmpty()) {
            fail(run, "자동 쇼츠로 게시할 클립이 없습니다")
            return
        }

        clips.filter { it.renderedVideoId == null }.forEach { clip ->
            val existing = renderJobRepository.findByRunAndClip(run.id, clip.id)
            if (existing?.status == ShortsRenderJobStatus.FAILED) {
                fail(run, existing.failureReason ?: "쇼츠 렌더링에 실패했습니다")
                return
            }
            // QUEUED/RUNNING은 requestRender가 같은 job을 반환하므로 재시작·재호출에도
            // 새 작업을 만들지 않는다. 완료됐지만 clip 연결만 늦은 경우도 다음 tick에서 확인한다.
            if (existing?.status != ShortsRenderJobStatus.COMPLETED) {
                renderUseCase.requestRender(run.userId, run.workspaceId, run.id, clip.id)
            }
        }

        val refreshed = shortsClipRepository.findByRunId(run.id)
            .filter { it.status != ClipStatus.DISCARDED }
        if (refreshed.any { it.renderedVideoId == null }) return

        val startAt = run.autoScheduleStartAt ?: runFailure(run, "자동 쇼츠 예약 시작 시각이 없습니다")
        val intervalHours = run.autoScheduleIntervalHours
            ?: runFailure(run, "자동 쇼츠 예약 간격이 없습니다")
        require(intervalHours > 0) { "자동 쇼츠 예약 간격은 1시간 이상이어야 합니다" }
        require(run.autoSchedulePlatforms.isNotEmpty()) { "자동 쇼츠 게시 대상 플랫폼이 없습니다" }

        // AWAITING_SCHEDULE -> PENDING을 version으로 선점한다. 두 인스턴스가 동시에
        // 발견해도 한 쪽만 이벤트를 발행하므로 외부 게시 중복을 막는다.
        val current = pipelineRunRepository.findById(run.id) ?: return
        if (current.status != PipelineRunStatus.AWAITING_SCHEDULE || !current.autoSchedule) return
        pipelineRunRepository.update(current.copy(status = PipelineRunStatus.PENDING, errorMessage = null))
        eventPublisher.publishEvent(
            ShortsPipelineEvent(
                runId = run.id,
                fromStage = PipelineStage.SCHEDULE,
                scheduleStartAt = startAt,
                scheduleIntervalHours = intervalHours,
                platforms = run.autoSchedulePlatforms,
            ),
        )
    }

    private fun fail(run: PipelineRun, reason: String) {
        val current = pipelineRunRepository.findById(run.id) ?: return
        if (current.status == PipelineRunStatus.AWAITING_SCHEDULE) {
            pipelineRunRepository.update(current.copy(status = PipelineRunStatus.FAILED, errorMessage = reason))
        }
    }

    private fun runFailure(run: PipelineRun, reason: String): Nothing {
        fail(run, reason)
        error(reason)
    }

    companion object {
        private const val BATCH_SIZE = 20
    }
}
