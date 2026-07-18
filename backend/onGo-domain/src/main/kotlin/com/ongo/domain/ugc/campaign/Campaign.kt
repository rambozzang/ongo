package com.ongo.domain.ugc.campaign

import java.time.LocalDateTime

/**
 * UGC 캠페인 애그리게잇.
 *
 * 금액은 최소 화폐 단위 [Long]으로 처리한다(KRW는 원 단위).
 * 상태 전이는 아래 도메인 메서드로만 수행하며, 허용되지 않은 전이는
 * `IllegalStateException`(→ HTTP 400)으로 처리한다.
 */
data class Campaign(
    val id: Long? = null,
    val workspaceId: Long,
    val name: String,
    val description: String? = null,
    val status: CampaignStatus = CampaignStatus.DRAFT,
    val objective: String = "AWARENESS",
    val totalBudget: Long = 0,
    val currency: String = "KRW",
    val fixedRewardPerCreator: Long = 0,
    val startAt: LocalDateTime? = null,
    val endAt: LocalDateTime? = null,
    val createdBy: Long,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val version: Long = 0,
) {
    init {
        require(name.isNotBlank()) { "캠페인 이름은 비어 있을 수 없습니다" }
        require(totalBudget >= 0) { "예산은 0 이상이어야 합니다" }
        require(fixedRewardPerCreator >= 0) { "보상액은 0 이상이어야 합니다" }
        require(endAt == null || startAt == null || endAt.isAfter(startAt)) {
            "종료일은 시작일보다 이후여야 합니다"
        }
    }

    /** DRAFT → RECRUITING. 공개 조건: 활성 플레이북 존재 + 시작/종료일 지정. */
    fun publish(hasActivePlaybook: Boolean): Campaign {
        requireTransition(CampaignStatus.RECRUITING)
        if (!hasActivePlaybook) {
            throw IllegalStateException("플레이북이 없는 캠페인은 공개할 수 없습니다")
        }
        if (startAt == null || endAt == null) {
            throw IllegalStateException("시작일과 종료일을 설정해야 공개할 수 있습니다")
        }
        return copy(status = CampaignStatus.RECRUITING)
    }

    /** RECRUITING/ACTIVE → PAUSED. */
    fun pause(): Campaign {
        requireTransition(CampaignStatus.PAUSED)
        return copy(status = CampaignStatus.PAUSED)
    }

    /** PAUSED → RECRUITING. */
    fun resume(): Campaign {
        requireTransition(CampaignStatus.RECRUITING)
        return copy(status = CampaignStatus.RECRUITING)
    }

    /** RECRUITING/ACTIVE → COMPLETED. */
    fun complete(): Campaign {
        requireTransition(CampaignStatus.COMPLETED)
        return copy(status = CampaignStatus.COMPLETED)
    }

    /** DRAFT/RECRUITING/PAUSED → CANCELLED. */
    fun cancel(): Campaign {
        requireTransition(CampaignStatus.CANCELLED)
        return copy(status = CampaignStatus.CANCELLED)
    }

    /** DRAFT 상태에서만 핵심 정보(예산·일정·목표 등) 수정을 허용한다. */
    fun assertEditable() {
        if (status != CampaignStatus.DRAFT) {
            throw IllegalStateException("DRAFT 상태의 캠페인만 수정할 수 있습니다 (현재: $status)")
        }
    }

    private fun requireTransition(target: CampaignStatus) {
        if (!status.canTransitionTo(target)) {
            throw IllegalStateException("허용되지 않은 상태 전이입니다: $status → $target")
        }
    }
}
