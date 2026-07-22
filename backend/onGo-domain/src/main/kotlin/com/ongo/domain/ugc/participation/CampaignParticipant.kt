package com.ongo.domain.ugc.participation

import java.time.LocalDateTime

/**
 * 수락된 크리에이터의 캠페인 참여. 캠페인당 크리에이터 1건(UNIQUE campaign_id, creator_id).
 * 금액은 최소 화폐 단위 [Long].
 */
data class CampaignParticipant(
    val id: Long? = null,
    val campaignId: Long,
    val creatorId: Long,
    val agreedReward: Long = 0,
    val active: Boolean = true,
    val joinedAt: LocalDateTime? = null,
) {
    init {
        require(agreedReward >= 0) { "약정 보상은 0 이상이어야 합니다" }
    }
}
