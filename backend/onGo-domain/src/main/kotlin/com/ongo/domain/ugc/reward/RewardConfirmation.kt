package com.ongo.domain.ugc.reward

import java.time.LocalDateTime

/**
 * 참여자당 보상 확정. 금액은 최소 화폐 단위 [Long].
 * 확정(CONFIRMED) 전(DRAFT)에만 금액을 수정할 수 있고, 확정 총액의 예산 초과 검증은 유스케이스에서 수행한다.
 */
data class RewardConfirmation(
    val id: Long? = null,
    val participantId: Long,
    val campaignId: Long,
    val creatorId: Long,
    val baseAmount: Long = 0,
    val bonusAmount: Long = 0,
    val totalAmount: Long = 0,
    val status: RewardStatus = RewardStatus.DRAFT,
    val note: String? = null,
    val confirmedBy: Long? = null,
    val confirmedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
) {
    init {
        require(baseAmount >= 0 && bonusAmount >= 0 && totalAmount >= 0) { "보상 금액은 0 이상이어야 합니다" }
    }

    /** DRAFT 상태에서 금액·메모를 갱신한다. total = base + bonus. */
    fun withAmounts(base: Long, bonus: Long, note: String?): RewardConfirmation {
        assertEditable()
        return copy(baseAmount = base, bonusAmount = bonus, totalAmount = base + bonus, note = note)
    }

    fun confirm(confirmedBy: Long): RewardConfirmation {
        requireTransition(RewardStatus.CONFIRMED)
        return copy(status = RewardStatus.CONFIRMED, confirmedBy = confirmedBy)
    }

    fun markPaid(): RewardConfirmation {
        requireTransition(RewardStatus.PAID_EXTERNALLY)
        return copy(status = RewardStatus.PAID_EXTERNALLY)
    }

    fun cancel(): RewardConfirmation {
        requireTransition(RewardStatus.CANCELLED)
        return copy(status = RewardStatus.CANCELLED)
    }

    fun assertEditable() {
        if (status != RewardStatus.DRAFT) {
            throw IllegalStateException("확정 전(DRAFT) 보상만 수정할 수 있습니다 (현재: $status)")
        }
    }

    private fun requireTransition(target: RewardStatus) {
        if (!status.canTransitionTo(target)) {
            throw IllegalStateException("허용되지 않은 보상 상태 전이입니다: $status → $target")
        }
    }
}
