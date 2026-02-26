package com.ongo.domain.platformhealth

interface PlatformHealthScoreRepository {
    fun findByWorkspaceId(workspaceId: Long): List<PlatformHealthScore>
    fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): PlatformHealthScore?
}
