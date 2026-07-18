package com.ongo.domain.ugc.campaign

/**
 * 캠페인 상태 머신.
 *
 * ```
 * DRAFT → RECRUITING → ACTIVE → COMPLETED
 *                     ↘ PAUSED
 * DRAFT/RECRUITING/PAUSED → CANCELLED
 * ```
 *
 * 상태 전이는 [Campaign]의 명시적 도메인 메서드로만 수행하며,
 * 허용되지 않은 전이는 `IllegalStateException`으로 처리한다.
 */
enum class CampaignStatus {
    DRAFT,
    RECRUITING,
    ACTIVE,
    PAUSED,
    COMPLETED,
    CANCELLED;

    fun canTransitionTo(target: CampaignStatus): Boolean = target in allowedTransitions()

    fun isTerminal(): Boolean = this == COMPLETED || this == CANCELLED

    private fun allowedTransitions(): Set<CampaignStatus> = when (this) {
        DRAFT -> setOf(RECRUITING, CANCELLED)
        RECRUITING -> setOf(ACTIVE, PAUSED, COMPLETED, CANCELLED)
        ACTIVE -> setOf(PAUSED, COMPLETED)
        PAUSED -> setOf(RECRUITING, ACTIVE, CANCELLED)
        COMPLETED -> emptySet()
        CANCELLED -> emptySet()
    }
}
