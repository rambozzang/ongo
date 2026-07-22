package com.ongo.domain.ugc.participation

/**
 * 지원 상태 머신.
 *
 * ```
 * APPLIED → ACCEPTED | REJECTED | WITHDRAWN
 * ```
 *
 * ACCEPTED/REJECTED/WITHDRAWN은 종료 상태다.
 */
enum class ApplicationStatus {
    APPLIED,
    ACCEPTED,
    REJECTED,
    WITHDRAWN;

    fun canTransitionTo(target: ApplicationStatus): Boolean =
        this == APPLIED && target in setOf(ACCEPTED, REJECTED, WITHDRAWN)

    fun isTerminal(): Boolean = this != APPLIED
}
