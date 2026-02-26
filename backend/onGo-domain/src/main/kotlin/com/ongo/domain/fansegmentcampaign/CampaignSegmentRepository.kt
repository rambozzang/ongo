package com.ongo.domain.fansegmentcampaign

interface CampaignSegmentRepository {
    fun findById(id: Long): CampaignSegment?
    fun findByUserId(userId: Long): List<CampaignSegment>
    fun save(segment: CampaignSegment): CampaignSegment
}
