package com.ongo.domain.ugc.participation

interface ApplicationRepository {
    fun findById(id: Long): CampaignApplication?

    fun findByCampaignIdAndCreatorId(campaignId: Long, creatorId: Long): CampaignApplication?

    fun findByCampaignId(campaignId: Long, status: String?, offset: Int, limit: Int): List<CampaignApplication>

    fun countByCampaignId(campaignId: Long, status: String?): Long

    fun findByCreatorId(creatorId: Long, offset: Int, limit: Int): List<CampaignApplication>

    fun countByCreatorId(creatorId: Long): Long

    fun save(application: CampaignApplication): CampaignApplication

    /** 상태 전이 결과를 저장한다(status, decided_by, decided_at 갱신). */
    fun updateStatus(application: CampaignApplication): CampaignApplication
}
