package com.ongo.domain.faninsights

interface FanSegmentRepository {
    fun findByWorkspaceId(workspaceId: Long): List<FanSegment>
}
