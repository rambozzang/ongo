package com.ongo.domain.ai

import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

enum class PipelineStatus {
    PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
}

enum class PipelineStepStatus {
    PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
}

data class AiPipeline(
    val id: String,
    val userId: Long,
    val videoId: Long,
    val channelId: Long? = null,
    val steps: List<AiPipelineStep>,
    var currentStep: AiPipelineStep? = null,
    var status: PipelineStatus = PipelineStatus.PENDING,
    val stepStatuses: MutableMap<AiPipelineStep, PipelineStepStatus> = ConcurrentHashMap(),
    val results: MutableMap<AiPipelineStep, Any?> = ConcurrentHashMap(),
    val errors: MutableMap<AiPipelineStep, String?> = ConcurrentHashMap(),
    val totalCreditsCharged: Int = 0,
    /**
     * 이미 환불한 금액. **0보다 크면 정산이 끝난 것**이다.
     *
     * 메모리 값은 참고용이며 멱등 판정은 DB 가 한다
     * ([AiPipelineRepository.settleRefund] 의 조건부 갱신).
     */
    val refundedCredits: Int = 0,
    /**
     * 차감 출처 분해. **`null` 이면 자동 환불하지 않는다.**
     *
     * 정산은 차감이 일어난 요청과 다른 요청에서 실행되므로 인메모리 영수증을 가질 수
     * 없다. 이 스냅샷이 그 자리를 대신한다 — [PipelineCreditAllocation] 참고.
     *
     * `null` 인 경우는 V108 마이그레이션 이전에 만들어진 행뿐이다. 출처를 모르는 채
     * 무료분으로 돌려주면 구매분이 소실되므로, 그 경로는 fail-closed 로 막고 운영
     * 수기 정산으로 넘긴다.
     */
    val creditAllocation: PipelineCreditAllocation? = null,
    val discountApplied: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
) {
    init {
        steps.forEach { stepStatuses[it] = PipelineStepStatus.PENDING }
    }
}

/**
 * AI 파이프라인은 크레딧을 차감한 뒤 실행되므로 메모리 맵을 진실의 원천으로
 * 삼을 수 없다. 구현체는 이 상태를 DB에 저장하고 재시작 뒤 active job을 복구한다.
 */
interface AiPipelineRepository {
    fun findById(id: String): AiPipeline?
    fun findActive(limit: Int): List<AiPipeline>
    /** PENDING 또는 오래된 RUNNING 하나만 원자적으로 실행 상태로 선점한다. */
    fun claimForExecution(id: String, now: java.time.LocalDateTime, staleBefore: java.time.LocalDateTime): AiPipeline?
    fun save(pipeline: AiPipeline): AiPipeline

    /**
     * **아직 환불하지 않은 파이프라인만** 정산 완료로 표시한다.
     *
     * 조건(`refunded_credits = 0`)을 WHERE 에 두는 것이 핵심이다. 읽고-판단하고-쓰면
     * 실행 스레드의 자연 실패 정산과 사용자의 취소 요청이 동시에 통과해 같은 금액을 두 번
     * 돌려준다. 여기서는 DB 가 승자를 정하고, 진 쪽은 false 를 받아 환불하지 않는다.
     *
     * 상태·완료 시각도 같은 문장에서 바꿔, 환불 표식만 남고 상태가 뒤처지는 창을 없앤다.
     *
     * @return 이번 호출이 정산을 확정했으면 true. 이미 정산됐으면 false.
     */
    fun settleRefund(
        id: String,
        refundedCredits: Int,
        status: PipelineStatus,
        completedAt: java.time.LocalDateTime,
    ): Boolean
}
