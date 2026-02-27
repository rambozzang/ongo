package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.hashtaganalytics.*
import org.springframework.stereotype.Repository

@Repository
class HashtagPerformanceStubRepository : HashtagPerformanceRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<HashtagPerformance> = emptyList()
    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<HashtagPerformance> = emptyList()
    override fun findById(id: Long): HashtagPerformance? = null
    override fun save(performance: HashtagPerformance): HashtagPerformance = performance.copy(id = 1)
    override fun update(performance: HashtagPerformance): HashtagPerformance = performance
    override fun deleteById(id: Long) {}
}

@Repository
class HashtagGroupStubRepository : HashtagGroupRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<HashtagGroup> = emptyList()
    override fun findById(id: Long): HashtagGroup? = null
    override fun save(group: HashtagGroup): HashtagGroup = group.copy(id = 1)
    override fun deleteById(id: Long) {}
}
