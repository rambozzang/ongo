package com.ongo.domain.ugc.participation

interface InviteRepository {
    fun findByTokenHash(tokenHash: String): CampaignInvite?

    fun findByCampaignId(campaignId: Long): List<CampaignInvite>

    fun save(invite: CampaignInvite): CampaignInvite

    /** 사용 횟수를 원자적으로 1 증가시킨다. */
    fun incrementUsedCount(id: Long)
}
