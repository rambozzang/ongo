package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.faninsights.*
import org.springframework.stereotype.Repository

@Repository
class FanBehaviorStubRepository : FanBehaviorRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<FanBehavior> = emptyList()
    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<FanBehavior> = emptyList()
}

@Repository
class FanDemographicStubRepository : FanDemographicRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<FanDemographic> = emptyList()
    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<FanDemographic> = emptyList()
}

@Repository
class FanSegmentStubRepository : FanSegmentRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<FanSegment> = emptyList()
}
