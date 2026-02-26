package com.ongo.domain.collaborationboard

interface BoardActivityRepository {
    fun findByWorkspaceId(workspaceId: Long, limit: Int = 20): List<BoardActivity>
    fun save(activity: BoardActivity): BoardActivity
}
