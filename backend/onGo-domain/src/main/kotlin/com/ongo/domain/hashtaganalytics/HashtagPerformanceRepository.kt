package com.ongo.domain.hashtaganalytics

interface HashtagPerformanceRepository {
    fun findByWorkspaceId(workspaceId: Long): List<HashtagPerformance>
    fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<HashtagPerformance>
    fun findById(id: Long): HashtagPerformance?
    fun save(performance: HashtagPerformance): HashtagPerformance
    fun update(performance: HashtagPerformance): HashtagPerformance
    fun deleteById(id: Long)
}
