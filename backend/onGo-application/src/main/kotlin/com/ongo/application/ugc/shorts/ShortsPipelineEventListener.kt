package com.ongo.application.ugc.shorts

import com.ongo.application.ugc.shorts.stage.ScheduleParams
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 쇼츠 파이프라인 이벤트 리스너. VideoPublishEventListener와 같은 패턴:
 * 트랜잭션 커밋 후 가상 스레드에서 오케스트레이터를 실행한다. 예외는 로그만 남긴다
 * (실패 상태 기록은 오케스트레이터가 한다).
 */
@Component
class ShortsPipelineEventListener(
    private val orchestrator: ShortsPipelineOrchestrator,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePipelineRun(event: ShortsPipelineEvent) {
        log.info("쇼츠 파이프라인 실행 이벤트 수신: runId={}, fromStage={}", event.runId, event.fromStage)
        try {
            val schedule = if (event.scheduleStartAt != null && event.scheduleIntervalHours != null) {
                ScheduleParams(
                    startAt = event.scheduleStartAt,
                    intervalHours = event.scheduleIntervalHours,
                    platforms = event.platforms,
                )
            } else {
                null
            }
            orchestrator.run(event.runId, event.fromStage, schedule)
        } catch (e: Exception) {
            log.error("쇼츠 파이프라인 실행 중 예외: runId={}", event.runId, e)
        }
    }
}
