package com.ongo.domain.ugc.publishing

interface CampaignPostRepository {
    fun findById(id: Long): CampaignPost?

    fun findAll(): List<CampaignPost>

    fun findByCampaignId(campaignId: Long): List<CampaignPost>

    fun findBySubmissionId(submissionId: Long): List<CampaignPost>

    fun findByIdempotencyKey(idempotencyKey: String): CampaignPost?

    fun save(post: CampaignPost): CampaignPost

    fun updateStatus(id: Long, status: PostStatus, platformPostId: String?, errorMessage: String?)
}
