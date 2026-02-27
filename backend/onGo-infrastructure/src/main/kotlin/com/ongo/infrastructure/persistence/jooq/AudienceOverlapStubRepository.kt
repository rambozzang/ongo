package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.audienceoverlap.*
import org.springframework.stereotype.Repository

@Repository
class AudienceOverlapResultStubRepository : AudienceOverlapResultRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<AudienceOverlapResult> = emptyList()
    override fun save(result: AudienceOverlapResult): AudienceOverlapResult = result.copy(id = 1)
}

@Repository
class OverlapSegmentStubRepository : OverlapSegmentRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<OverlapSegment> = emptyList()
}
