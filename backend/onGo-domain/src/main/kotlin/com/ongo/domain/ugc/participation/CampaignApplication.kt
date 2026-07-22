package com.ongo.domain.ugc.participation

import java.time.LocalDateTime

/**
 * 캠페인 지원. 크리에이터(=userId)당 캠페인 1건(UNIQUE campaign_id, creator_id).
 * 상태 전이는 도메인 메서드로만 수행하며 허용되지 않은 전이는 `IllegalStateException`.
 */
data class CampaignApplication(
    val id: Long? = null,
    val campaignId: Long,
    val creatorId: Long,
    val message: String? = null,
    val portfolioUrl: String? = null,
    val status: ApplicationStatus = ApplicationStatus.APPLIED,
    val decidedBy: Long? = null,
    val decidedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
) {
    fun accept(deciderId: Long): CampaignApplication {
        requireTransition(ApplicationStatus.ACCEPTED)
        return copy(status = ApplicationStatus.ACCEPTED, decidedBy = deciderId)
    }

    fun reject(deciderId: Long): CampaignApplication {
        requireTransition(ApplicationStatus.REJECTED)
        return copy(status = ApplicationStatus.REJECTED, decidedBy = deciderId)
    }

    fun withdraw(): CampaignApplication {
        requireTransition(ApplicationStatus.WITHDRAWN)
        return copy(status = ApplicationStatus.WITHDRAWN)
    }

    private fun requireTransition(target: ApplicationStatus) {
        if (!status.canTransitionTo(target)) {
            throw IllegalStateException("허용되지 않은 지원 상태 전이입니다: $status → $target")
        }
    }
}
