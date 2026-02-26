package com.ongo.domain.audienceoverlap

interface AudienceOverlapResultRepository {
    fun findByWorkspaceId(workspaceId: Long): List<AudienceOverlapResult>
    fun save(result: AudienceOverlapResult): AudienceOverlapResult
}
