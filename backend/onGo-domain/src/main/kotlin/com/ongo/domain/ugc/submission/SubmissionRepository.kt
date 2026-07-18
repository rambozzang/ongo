package com.ongo.domain.ugc.submission

interface SubmissionRepository {
    fun findById(id: Long): ContentSubmission?

    fun findByCampaignIdAndCreatorId(campaignId: Long, creatorId: Long): ContentSubmission?

    fun findByCampaignId(campaignId: Long, status: String?, offset: Int, limit: Int): List<ContentSubmission>

    fun countByCampaignId(campaignId: Long, status: String?): Long

    /** 제출 생성 + 첨부물 저장. */
    fun save(submission: ContentSubmission): ContentSubmission

    /** caption/status/revision/첨부물 갱신(첨부물은 전체 교체). */
    fun update(submission: ContentSubmission): ContentSubmission

    /** 상태·시각(submitted_at/approved_at)만 갱신. */
    fun updateStatus(submission: ContentSubmission): ContentSubmission
}
