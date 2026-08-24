package com.ongo.application.ugc.shorts

import com.ongo.application.activitylog.ActivityLogActions
import com.ongo.application.activitylog.ActivityLogUseCase
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

/**
 * 완성 영상이 연결된 사건을 퍼널 로그에 남긴다.
 *
 * ## 신뢰성 경계
 *
 * **기록은 산출물을 되돌릴 수 없다.** 이 리스너가 불릴 때 영상 레코드와 클립 연결은
 * 이미 커밋돼 있다. 고객은 결과를 받아갈 수 있는 상태다. 그 시점에 기록용 INSERT 가
 * 실패했다고 무언가를 되돌리면, 만들어 놓은 결과물을 측정 때문에 버리는 셈이다.
 *
 * 두 겹으로 끊는다.
 *
 * 1. [TransactionPhase.AFTER_COMMIT] — 연결 트랜잭션이 커밋된 뒤에만 불린다. 롤백되면
 *    아예 호출되지 않으므로, 연결되지 않은 클립이 가용으로 기록되는 일이 없다.
 * 2. 아래 `runCatching` — 협력자의 내부 삼킴에 기대지 않고 여기서 닫는다.
 *    [ActivityLogUseCase.logActivityIndependently] 도 실패를 삼키지만 그건 그 클래스의
 *    구현 세부다. 삼킴이 사라지거나 그 안쪽에 닿기 전에 던지는 경로가 생기면 예외가
 *    이 콜백 밖으로 나가고, 렌더 실행 스레드나 `attachRenderedVideo` 응답이 깨진다.
 *    이 클래스만 읽어도 "기록은 결과물에 영향을 줄 수 없다"가 성립해야 한다.
 *
 * ## 비동기로 두지 않은 이유
 *
 * 위 [ShortsPipelineEventListener] 는 `@Async` 를 쓰지만 그쪽은 파이프라인 전체를 돌린다.
 * 여기는 INSERT 한 번이라 스레드를 넘길 이득이 없고, 동기로 두면 테스트가 스케줄러
 * 타이밍에 기대지 않아도 된다.
 */
@Component
class ShortsClipAvailableActivityListener(
    private val activityLogUseCase: ActivityLogUseCase,
) {

    private val log = LoggerFactory.getLogger(ShortsClipAvailableActivityListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onClipAvailable(event: ShortsClipAvailableEvent) {
        runCatching {
            activityLogUseCase.logActivityIndependently(
                userId = event.userId,
                action = ActivityLogActions.SHORTS_CLIP_AVAILABLE,
                entityType = ActivityLogActions.ENTITY_SHORTS_RUN,
                /*
                 * 실행 id 다. 퍼널의 단위가 사용자와 실행이지 클립이 아니다 — clipId 를
                 * 쓰면 순서 조건 집계가 클립 단위로 흩어진다. 클립별 상세는
                 * ugc_shorts_clips 를 조인해 읽는다.
                 */
                entityId = event.runId,
            )
        }.onFailure { cause ->
            // 스토리지 키·URL·파일명은 남기지 않는다. 추적에 필요한 것은 내부 식별자뿐이다.
            log.warn(
                "클립 가용 활동 로그 기록 실패. 결과물은 이미 연결됐다. runId={} clipId={}",
                event.runId, event.clipId, cause,
            )
        }
    }
}
