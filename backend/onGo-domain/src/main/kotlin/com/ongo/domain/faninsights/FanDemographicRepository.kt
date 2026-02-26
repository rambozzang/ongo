package com.ongo.domain.faninsights

interface FanDemographicRepository {
    fun findByWorkspaceId(workspaceId: Long): List<FanDemographic>
    fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<FanDemographic>
}
