package com.ongo.domain.faninsights

interface FanBehaviorRepository {
    fun findByWorkspaceId(workspaceId: Long): List<FanBehavior>
    fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<FanBehavior>
}
