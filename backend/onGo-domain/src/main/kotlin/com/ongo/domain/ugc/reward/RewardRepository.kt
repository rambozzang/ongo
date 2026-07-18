package com.ongo.domain.ugc.reward

interface RewardRepository {
    fun findById(id: Long): RewardConfirmation?

    fun findByParticipantId(participantId: Long): RewardConfirmation?

    fun findByCampaignId(campaignId: Long): List<RewardConfirmation>

    /** 확정/지급(CONFIRMED, PAID_EXTERNALLY) 상태의 total_amount 합계. 예산 초과 검증에 사용한다. */
    fun sumSettledTotalByCampaign(campaignId: Long): Long

    fun save(reward: RewardConfirmation): RewardConfirmation

    fun update(reward: RewardConfirmation): RewardConfirmation
}
