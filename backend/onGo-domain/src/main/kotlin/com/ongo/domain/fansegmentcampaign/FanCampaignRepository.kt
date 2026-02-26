package com.ongo.domain.fansegmentcampaign

interface FanCampaignRepository {
    fun findById(id: Long): FanCampaign?
    fun findByUserId(userId: Long): List<FanCampaign>
    fun save(campaign: FanCampaign): FanCampaign
    fun updateStatus(id: Long, status: String)
}
