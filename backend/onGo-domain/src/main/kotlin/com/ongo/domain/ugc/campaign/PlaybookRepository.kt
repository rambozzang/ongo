package com.ongo.domain.ugc.campaign

interface PlaybookRepository {
    fun findByCampaignId(campaignId: Long): Playbook?

    fun existsByCampaignId(campaignId: Long): Boolean

    /** 캠페인당 활성 플레이북 1개를 upsert하고 단계 목록을 교체한다. */
    fun upsert(playbook: Playbook): Playbook
}
