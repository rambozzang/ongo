package com.ongo.application.ugc.shorts

import com.ongo.application.credit.CreditAllocation
import com.ongo.application.credit.CreditService
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ai.PipelineCreditAllocation
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.RunStage
import com.ongo.domain.ugc.shorts.RunStageRepository
import com.ongo.domain.ugc.shorts.RunStageStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 차감과 함께 열린 단계. 영수증은 DB 에도 같은 내용이 남아 있다. */
data class ChargedStage(
    val runStage: RunStage,
    val allocation: CreditAllocation,
)

/**
 * 분해를 모르는 단계는 **자동 정산하지 않는다.**
 *
 * V111 이전에 만들어진 행은 차감 출처를 알 수 없다. 총액을 무료분으로 돌려주면 구매분이
 * 유효기간 있는 것으로 바뀌거나 `free_monthly` 한도에 걸려 증발한다 — 분해 컬럼이 막으려는
 * 바로 그 손실이다.
 *
 * 그렇다고 **정산 완료로 표시해서도 안 된다.** 실제로 한 푼도 돌려주지 않은 행이 "정산됨"
 * 으로 남으면 수기 복구와 감사가 근거를 잃는다. 그래서 표식도 세우지 않고 이 예외를 던져,
 * 호출자가 지우기·재실행을 **중단**하고 사람이 처리하게 한다.
 */
class ShortsStageSettlementRequiresManualReviewException(
    val runId: Long,
    val stageId: Long,
    val amount: Int,
) : BusinessException(
    "SHORTS_STAGE_SETTLEMENT_MANUAL_REVIEW",
    "이전 시도에 정산되지 않은 크레딧이 있어 진행할 수 없습니다. 고객지원에 문의해 주세요.",
)

/**
 * 쇼츠 단계의 **차감과 정산**을 한 곳에서 처리한다.
 *
 * ## 왜 별도 빈인가
 *
 * 두 연산 모두 **자기 트랜잭션 경계**가 필요한데, 오케스트레이터는 트랜잭션 없이 돌면서
 * 외부 AI 를 호출한다. 같은 클래스의 메서드를 부르면 프록시를 지나지 않아 `@Transactional`
 * 이 무시되므로, 경계를 갖는 쪽을 별도 빈으로 분리한다.
 *
 * 같은 이유로 **이 클래스 안에서 `@Transactional` 메서드끼리 부르지 않는다.** 공개 진입점만
 * 애노테이션을 갖고, 실제 정산은 그 트랜잭션에 참여하는 private 헬퍼가 한다.
 *
 * ## 무엇을 보장하는가
 *
 *  - **차감과 영수증 저장이 한 커밋이다.** 커밋된 뒤에는 어느 프로세스에서 죽어도 DB 에
 *    분해가 남아 있어 정확히 되돌릴 수 있다. 저장이 실패하면 차감도 함께 롤백된다.
 *  - **정산은 정확히 한 번이다.** 표식(조건부 갱신)과 실제 환불이 같은 트랜잭션에 있어,
 *    이긴 쪽만 환불하고 환불이 실패하면 표식도 롤백되어 다음 시도가 다시 집는다.
 *  - **정산이 실패하면 아무것도 지우지 않는다.** 실패를 삼키고 진행하면 되돌릴 근거가
 *    사라진 채 단계 행만 없어진다.
 */
@Service
class ShortsStageCreditService(
    private val creditService: CreditService,
    private val runStageRepository: RunStageRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 단계를 열면서 필요한 만큼 차감한다. **차감과 단계 행 저장이 한 커밋이다.**
     *
     * `REQUIRES_NEW` 인 이유는 오케스트레이터가 트랜잭션 없이 돌기 때문이다. 여기서 경계를
     * 열지 않으면 `validateAndDeduct`(REQUIRED)가 자기 트랜잭션으로 **혼자** 커밋하고, 이어지는
     * 단계 행 저장이 실패해도 차감은 남는다 — 되돌릴 근거 없이 돈만 빠진 상태다.
     *
     * 이 메서드가 예외를 던지면 트랜잭션 전체가 롤백되므로 **차감도 없던 일이 된다.**
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun chargeAndOpenStage(
        runId: Long,
        userId: Long,
        stage: PipelineStage,
        featureName: String?,
        creditCost: Int,
    ): ChargedStage {
        val allocation = if (featureName != null && creditCost > 0) {
            creditService.validateAndDeduct(userId, creditCost, featureName)
        } else {
            CreditAllocation.empty(userId, featureName ?: stage.name)
        }

        /*
         * 분해를 **차감과 같은 커밋에** 남긴다. 이 줄 다음에서 프로세스가 죽어도 복구기가
         * 이 행을 찾아 정확히 같은 자리로 되돌릴 수 있다.
         *
         * `creditCost` 도 여기서 쓴다. 예전에는 완료 시점에만 기록해, 진행 중인 단계의
         * 청구액이 0 으로 보였고 정산이 얼마를 돌려줘야 하는지 알 수 없었다.
         */
        val saved = runStageRepository.save(
            RunStage(
                runId = runId,
                stage = stage,
                status = RunStageStatus.RUNNING,
                creditCost = allocation.total,
                creditAllocation = allocation.takeIf { it.total > 0 }?.toSnapshot(),
                startedAt = Instant.now(),
            ),
        )
        return ChargedStage(saved, allocation)
    }

    /**
     * 단계 하나를 정산한다. **표식과 환불이 같은 트랜잭션이다.**
     *
     * 오케스트레이터의 단계 실패 경로가 쓴다. 인메모리 영수증이 아니라 **저장된 분해**로
     * 환불하므로, 복구기·재실행·삭제와 같은 경로를 지나고 같은 조건부 갱신으로 경쟁한다.
     * 예전처럼 환불을 먼저 하고 표식을 나중에 세우면, 표식이 실패했을 때 복구기가 같은
     * 단계를 다시 환불한다.
     *
     * @return 이번 호출이 환불했으면 true. 다른 정산이 이미 끝냈으면 false.
     * @throws ShortsStageSettlementRequiresManualReviewException 분해를 모르는 행일 때.
     */
    @Transactional
    fun settleStage(userId: Long, stage: RunStage, reason: String): Boolean =
        settleOne(userId, stage, reason)

    /**
     * 지울 범위의 미정산 단계를 **모두** 정산한다. 재실행·삭제가 단계 행을 지우기 **전에** 부른다.
     *
     * ## 실패를 삼키지 않는다
     *
     * 예외를 그대로 올린다. 호출자(`rerunStage`·`deleteRun`)는 `@Transactional` 이므로 그
     * 트랜잭션이 롤백되어 **단계 행이 지워지지 않는다.** 지운 뒤에는 분해가 사라져 되돌릴
     * 근거가 없으므로, 정산하지 못한 채 지우는 것은 사용자의 크레딧을 조용히 없애는 일이다.
     *
     * 롤백되므로 사용자가 다시 시도하면 같은 행을 다시 집는다 — 재시도 가능하다.
     *
     * ## 트랜잭션 경계
     *
     * `REQUIRED` 다. 호출자가 이미 트랜잭션을 열었으면 **그 트랜잭션에 참여해** 환불과 삭제가
     * 함께 커밋되거나 함께 롤백된다. 열지 않았으면(복구기) 여기서 연다.
     */
    @Transactional
    fun settleBeforeDiscard(runId: Long, userId: Long, fromSortOrder: Int, reason: String) {
        runStageRepository.findUnsettled(runId, fromSortOrder).forEach { stage ->
            settleOne(userId, stage, reason)
        }
    }

    /**
     * 실제 정산. **`@Transactional` 을 붙이지 않는다** — 이 클래스 안에서 불리므로 애노테이션을
     * 붙여도 프록시를 지나지 않아 무시된다. 호출한 공개 메서드의 트랜잭션에 그대로 참여한다.
     */
    private fun settleOne(userId: Long, stage: RunStage, reason: String): Boolean {
        /*
         * **분해가 없으면 표식도 세우지 않고 멈춘다.**
         *
         * 표식을 세우면 한 푼도 돌려주지 않은 행이 "정산됨" 으로 남아 수기 복구와 감사가
         * 근거를 잃는다. 자동 환불도, 자동 종료도 하지 않고 사람에게 넘긴다.
         */
        val snapshot = stage.creditAllocation
            ?: run {
                log.error(
                    "차감 분해가 없어 자동 정산할 수 없다. 수기 정산 대상이다. " +
                        "runId={} stageId={} stage={} userId={} amount={}",
                    stage.runId, stage.id, stage.stage, userId, stage.creditCost,
                )
                throw ShortsStageSettlementRequiresManualReviewException(
                    runId = stage.runId, stageId = stage.id, amount = stage.creditCost,
                )
            }

        // 표식 경쟁에서 지면 다른 정산이 이미 끝냈다는 뜻이다. 아무것도 하지 않는다.
        if (!runStageRepository.settleRefund(stage.id, stage.creditCost, reason)) return false

        /*
         * 환불이 던지면 이 트랜잭션이 롤백되어 **표식도 함께 되돌아간다.** 그래서 다음 시도가
         * 같은 행을 다시 집는다. 삼키면 표식만 남아 한 푼도 돌려주지 않은 채 재시도 대상에서
         * 빠지고, 사용자는 영영 잃는다.
         */
        creditService.refundAllocation(
            CreditAllocation.restored(
                userId = userId,
                featureName = stage.stage.name,
                freeAmount = snapshot.freeAmount,
                purchasedAmounts = snapshot.purchasedAmounts,
            ),
        )
        log.warn(
            "미정산 쇼츠 단계를 환불했다. runId={} stageId={} stage={} userId={} amount={}",
            stage.runId, stage.id, stage.stage, userId, stage.creditCost,
        )
        return true
    }

    private fun CreditAllocation.toSnapshot() = PipelineCreditAllocation(
        freeAmount = freeAmount,
        purchasedAmounts = purchasedPortions.associate { it.purchasedCreditId to it.amount },
    )
}
