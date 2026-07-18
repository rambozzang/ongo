package com.ongo.domain.ugc.participation

interface ParticipantRepository {
    fun existsByCampaignIdAndCreatorId(campaignId: Long, creatorId: Long): Boolean

    fun findByCampaignId(campaignId: Long): List<CampaignParticipant>

    fun findByCreatorId(creatorId: Long, offset: Int, limit: Int): List<CampaignParticipant>

    fun countByCreatorId(creatorId: Long): Long

    fun save(participant: CampaignParticipant): CampaignParticipant
}
