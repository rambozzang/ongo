package com.ongo.domain.ugc.reward

/**
 * 보상 확정 상태 머신.
 *
 * ```
 * DRAFT → CONFIRMED → PAID_EXTERNALLY
 * DRAFT/CONFIRMED → CANCELLED
 * ```
 *
 * MVP는 실화폐 자동 송금을 하지 않으므로 지급은 외부(수동) 처리 표시(PAID_EXTERNALLY)로 끝난다.
 */
enum class RewardStatus {
    DRAFT,
    CONFIRMED,
    PAID_EXTERNALLY,
    CANCELLED;

    fun canTransitionTo(target: RewardStatus): Boolean = target in allowed()

    /** 예산 소진으로 계산되는 확정/지급 상태. */
    fun isSettled(): Boolean = this == CONFIRMED || this == PAID_EXTERNALLY

    private fun allowed(): Set<RewardStatus> = when (this) {
        DRAFT -> setOf(CONFIRMED, CANCELLED)
        CONFIRMED -> setOf(PAID_EXTERNALLY, CANCELLED)
        PAID_EXTERNALLY -> emptySet()
        CANCELLED -> emptySet()
    }
}
