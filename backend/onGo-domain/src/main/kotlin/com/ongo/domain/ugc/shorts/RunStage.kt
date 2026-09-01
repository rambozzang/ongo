package com.ongo.domain.ugc.shorts

import java.time.Instant

/**
 * 단계 실행 기록. 사용한 프롬프트 개정·AI 제공자·크레딧·입출력 스냅샷을 남겨 추적 가능하게 한다.
 */
data class RunStage(
    val id: Long = 0,
    val runId: Long,
    val stage: PipelineStage,
    val status: RunStageStatus = RunStageStatus.PENDING,
    val promptId: Long? = null,
    val promptRevision: Int? = null,
    val aiProvider: String? = null,
    val creditCost: Int = 0,
    val inputSnapshot: String? = null,
    val outputSnapshot: String? = null,
    val errorMessage: String? = null,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,

    /**
     * 이 단계 차감의 **출처 분해**. 프로세스를 넘어서는 환불의 유일한 근거다.
     *
     * `null` 은 V111 이전에 만들어진 행이라는 뜻이며 **자동 환불 대상이 아니다** — 출처를
     * 모르는 채 총액을 무료분으로 돌려주면 구매분이 유효기간 있는 것으로 바뀌거나
     * `free_monthly` 한도에 걸려 증발한다.
     */
    val creditAllocation: com.ongo.domain.ai.PipelineCreditAllocation? = null,

    /**
     * 이미 환불한 크레딧. **0 은 "아직 정산하지 않음"** 이다.
     *
     * 0 보다 크면 정산이 끝난 것이며 다시 환불하지 않는다. 이 값은 정산 경로만 쓴다 —
     * 일반 갱신이 메모리의 기본값 0 으로 확정된 표식을 지우면 이중 환불이 열린다.
     */
    val refundedCredits: Int = 0,
)

/** 차감됐는데 아직 정산되지 않은 단계인가. 정산 대상 판정의 단일 근거다. */
val RunStage.isUnsettled: Boolean
    get() = status == RunStageStatus.RUNNING && refundedCredits == 0 && creditCost > 0
