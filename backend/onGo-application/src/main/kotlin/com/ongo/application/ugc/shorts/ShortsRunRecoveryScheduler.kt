package com.ongo.application.ugc.shorts

import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.PipelineRunStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * 크래시로 `RUNNING` 에 고착된 쇼츠 실행을 **사용자가 다시 시도할 수 있는 상태로만** 되돌린다.
 *
 * ## 무엇이 막혀 있었나
 *
 * [ShortsPipelineOrchestrator] 는 `@Transactional` 이 아니다. 단계마다 크레딧 차감이 자기
 * 트랜잭션으로 즉시 커밋되므로, 프로세스가 단계 도중 죽으면 **차감은 확정돼 있고 실패 경로의
 * 환불은 실행되지 않는다.** 그 상태에서 남는 길이 없었다 — 새 이벤트는 확보 조건(`PENDING`)을
 * 만족하지 못하고, 재실행 API 는 `RUNNING` 을 거절한다. 사용자에게 남은 선택은 실행을 지우고
 * 처음부터 다시 결제하는 것뿐이었다.
 *
 * 이 배포에는 프로세스 감시자가 없어(systemd 유닛은 작성만 되어 있고 설치되지 않았다) 크래시
 * 후 수동 재기동까지 시간이 걸리고, 그동안 만들어진 고착은 재기동만으로 풀리지 않는다.
 *
 * ## 왜 자동으로 다시 돌리지 않는가
 *
 * `PENDING` 으로 되돌리면 다음 이벤트가 곧바로 확보해 파이프라인이 자동으로 재실행된다.
 * 그런데 진척 신호(`updated_at`·`version`)는 **단계 경계에서만** 갱신되므로, 오래 걸리는
 * 단계 하나(긴 원본의 전사)를 죽은 것으로 오인할 수 있다. 그때 자동 재실행이 붙으면
 * **살아 있는 작업과 겹쳐 같은 단계가 두 번 청구된다** — 고치려던 것보다 나쁜 사고다.
 *
 * 그래서 이 복구기는 `FAILED` 로만 되돌린다. `FAILED` 는
 * [PipelineRunRepository.claimRunning] 의 조건이 아니므로 **어떤 작업도 다시 실행되지 않고
 * 어떤 크레딧도 청구되지 않는다.** 되돌려주는 것은 "사용자가 재실행을 누를 수 있는 상태" 뿐이고,
 * 다시 돌릴지는 사용자가 정한다.
 *
 * ## 살아 있는 작업을 건드리지 않는 두 겹
 *
 *  1. [ShortsPipelineOrchestrator.isActiveInThisProcess] — 이 JVM 이 들고 있는 실행은 건너뛴다.
 *     크래시로 죽었다면 새 프로세스의 레지스트리는 비어 있으므로 복구 대상이 된다. 렌더 복구기
 *     ([ShortsRenderRecoveryScheduler])가 같은 근거로 같은 보호를 한다.
 *  2. [PipelineRunRepository.failStale] — 관측한 `version` 과 `RUNNING` 을 함께 조건에 둔 조건부
 *     갱신이다. 읽은 뒤 살아 있는 작업이 단계를 하나라도 넘겼다면 0행이 되어 복구가 취소된다.
 *
 * 시간 임계값은 이 두 겹 위의 여유일 뿐 단독 근거가 아니다. 그래서 기본값을 렌더 복구기보다
 * 넉넉하게 잡는다.
 *
 * **다중 인스턴스 전제가 아니다.** 1번 보호는 같은 JVM 만 안다. 이 배포는 단일 인스턴스이며
 * (`start.sh` 의 PID 파일이 중복 기동을 거부한다) 렌더 복구기도 같은 전제 위에 서 있다.
 * 인스턴스를 늘린다면 두 복구기 모두 공유 잠금이 필요하다.
 */
@Component
class ShortsRunRecoveryScheduler(
    private val runRepository: PipelineRunRepository,
    private val orchestrator: ShortsPipelineOrchestrator,
    /** 미정산 단계 환불. 표식과 환불이 한 트랜잭션이라 정확히 한 번만 돌려준다. */
    private val stageCreditService: ShortsStageCreditService,
    /** 진척이 이만큼 멈춰 있어야 고착으로 본다. 가장 긴 단계보다 넉넉해야 한다. */
    @param:Value("\${shorts.pipeline.recovery-stale-after-ms:7200000}")
    private val staleAfterMs: Long,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${shorts.pipeline.recovery-delay-ms:300000}")
    fun recoverStuckRuns() {
        val now = Instant.now()
        val candidates = runRepository.findByStatus(PipelineRunStatus.RUNNING, BATCH_SIZE)
        if (candidates.isEmpty()) return

        candidates.forEach { run ->
            val id = run.id
            // 1) 이 프로세스가 들고 있으면 살아 있는 작업이다.
            if (orchestrator.isActiveInThisProcess(id)) return@forEach
            // 2) 아직 진척이 있었다면 고착이 아니다.
            if (Duration.between(run.updatedAt, now).toMillis() < staleAfterMs) return@forEach

            // 3) 읽은 뒤 진행이 있었다면 여기서 취소된다.
            val recovered = runCatching {
                runRepository.failStale(id, run.version, STUCK_REASON)
            }.onFailure { log.warn("고착 실행 복구 실패. runId={}", id, it) }.getOrDefault(false)

            if (recovered) {
                log.warn(
                    "고착된 쇼츠 실행을 재시도 가능 상태로 되돌렸다. runId={} userId={} lastUpdatedAt={}",
                    id, run.userId, run.updatedAt,
                )
                /*
                 * **CAS 가 이긴 뒤에만 환불한다.**
                 *
                 * `failStale` 성공은 "관측 이후 진행이 없었다" 는 증명이다. 그 증명 없이
                 * 환불하면 진행 중인 단계의 크레딧을 돌려주고 결과는 결과대로 나가는
                 * 이중 지급이 된다.
                 *
                 * 완료된 단계는 `findUnsettled` 가 애초에 돌려주지 않는다 — 실제로 일한
                 * 대가로 정당하게 청구된 것이다.
                 */
                /*
                 * 정산 실패를 여기서 삼키는 것은 **복구가 지우는 경로가 아니기** 때문이다.
                 * 실패하면 표식도 함께 롤백되어 단계가 `RUNNING` · 미정산으로 남는다.
                 *
                 * 다만 이 실행은 방금 `FAILED` 가 됐으므로 **위 조회(RUNNING 실행)에는 다시
                 * 잡히지 않는다.** 그래서 아래 [settleFailedRuns] 가 이미 실패한 실행의
                 * 미정산 단계를 따로 훑는다 — 그 경로가 없으면 환불하지 못한 단계가 사용자
                 * 조작 없이는 영영 재시도되지 않는다.
                 */
                runCatching {
                    stageCreditService.settleBeforeDiscard(id, run.userId, fromSortOrder = 0, reason = STUCK_REASON)
                }.onFailure { log.error("고착 실행의 단계 정산 실패. 다음 실행에서 다시 시도한다. runId={}", id, it) }
            }
        }
    }

    /**
     * 이미 `FAILED` 인 실행에 남은 **미정산 단계를 다시 정산한다.**
     *
     * ## 왜 별도 훑기가 필요한가
     *
     * 정산이 실패하면 단계는 `RUNNING` · `refunded_credits = 0` 으로 남아 재시도를 기다린다.
     * 그런데 그 시점의 **실행은 이미 `FAILED`** 다 — 고착 복구가 그렇게 만들었거나
     * 오케스트레이터의 단계 실패 경로가 그렇게 닫았다. `RUNNING` 실행만 훑으면 그 단계는
     * 다시 잡히지 않고, 사용자가 재실행·삭제를 누르기 전까지 크레딧이 묶인 채 남는다.
     *
     * 사용자 조작에 의존하지 않고 스스로 회복하도록 여기서 한 번 더 집는다.
     *
     * 후보는 **미정산 단계가 남은 실행만** 질의로 걸러 온다. 상태만 보고 앞에서 끊으면 영구히
     * 쌓이는 정산 완료 실행들이 자리를 차지해 훑기가 조용히 무력해진다.
     *
     * ## 살아 있는 작업을 건드리지 않는다
     *
     * `FAILED` 실행에는 진행 중인 작업이 없다 — 오케스트레이터는 단계가 끝난 뒤에만 실행을
     * 닫고, 고착 복구는 진척이 없었음을 version CAS 로 증명한 뒤에만 닫는다. 그래도
     * 생존 확인을 한 번 더 둔다.
     */
    @Scheduled(fixedDelayString = "\${shorts.pipeline.recovery-delay-ms:300000}")
    fun settleFailedRuns() {
        runRepository.findFailedWithUnsettledStages(BATCH_SIZE).forEach { run ->
            if (orchestrator.isActiveInThisProcess(run.id)) return@forEach
            runCatching {
                stageCreditService.settleBeforeDiscard(
                    runId = run.id,
                    userId = run.userId,
                    fromSortOrder = 0,
                    reason = SETTLE_RETRY_REASON,
                )
            }.onFailure {
                log.error("실패한 실행의 미정산 단계 정산 실패. 다음 실행에서 다시 시도한다. runId={}", run.id, it)
            }
        }
    }

    companion object {
        /** 정산만 다시 시도하는 경우의 사유. 실행 자체는 이미 실패로 닫혀 있다. */
        const val SETTLE_RETRY_REASON = "이전 시도의 크레딧을 정산했습니다."

        private const val BATCH_SIZE = 50

        /**
         * 사용자에게 그대로 보인다. **무엇이 일어났고 지금 무엇을 할 수 있는지**만 말한다.
         *
         * 이미 지나간 단계의 크레딧은 되돌리지 않는다 — 그 단계는 실제로 수행됐고, 어디까지
         * 됐는지는 `run_stages` 에 남아 있다. 없는 사실을 만들지 않기 위해 "환불했다"고 쓰지 않는다.
         */
        const val STUCK_REASON =
            "서버가 중단되어 실행이 멈췄습니다. 마지막으로 완료된 단계부터 다시 실행할 수 있습니다."
    }
}
