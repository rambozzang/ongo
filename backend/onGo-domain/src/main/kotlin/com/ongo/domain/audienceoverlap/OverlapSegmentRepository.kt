package com.ongo.domain.audienceoverlap

interface OverlapSegmentRepository {
    fun findByWorkspaceId(workspaceId: Long): List<OverlapSegment>
}
