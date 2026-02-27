package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.platformhealth.*
import org.springframework.stereotype.Repository

@Repository
class PlatformHealthScoreStubRepository : PlatformHealthScoreRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<PlatformHealthScore> = emptyList()
    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): PlatformHealthScore? = null
}

@Repository
class HealthIssueStubRepository : HealthIssueRepository {
    override fun findByHealthScoreId(healthScoreId: Long): List<HealthIssue> = emptyList()
}
