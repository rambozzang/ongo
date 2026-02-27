package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.fansegmentcampaign.*
import org.springframework.stereotype.Repository

@Repository
class FanCampaignStubRepository : FanCampaignRepository {
    override fun findById(id: Long): FanCampaign? = null
    override fun findByUserId(userId: Long): List<FanCampaign> = emptyList()
    override fun save(campaign: FanCampaign): FanCampaign = campaign.copy(id = 1)
    override fun updateStatus(id: Long, status: String) {}
}

@Repository
class CampaignSegmentStubRepository : CampaignSegmentRepository {
    override fun findById(id: Long): CampaignSegment? = null
    override fun findByUserId(userId: Long): List<CampaignSegment> = emptyList()
    override fun save(segment: CampaignSegment): CampaignSegment = segment.copy(id = 1)
}
