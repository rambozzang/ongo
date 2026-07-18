package com.ongo.domain.ugc.campaign

interface CampaignRepository {
    fun findById(id: Long): Campaign?

    /** 워크스페이스 내 캠페인 목록(상태/검색어 필터 + 페이지네이션). 최신순. */
    fun findByWorkspaceId(
        workspaceId: Long,
        status: String?,
        query: String?,
        offset: Int,
        limit: Int,
    ): List<Campaign>

    fun countByWorkspaceId(workspaceId: Long, status: String?, query: String?): Long

    fun save(campaign: Campaign): Campaign

    fun update(campaign: Campaign): Campaign
}
