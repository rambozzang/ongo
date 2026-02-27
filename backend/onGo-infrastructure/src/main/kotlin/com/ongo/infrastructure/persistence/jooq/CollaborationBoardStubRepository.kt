package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.collaborationboard.*
import org.springframework.stereotype.Repository

@Repository
class BoardTaskStubRepository : BoardTaskRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<BoardTask> = emptyList()
    override fun findByWorkspaceIdAndColumn(workspaceId: Long, columnType: String): List<BoardTask> = emptyList()
    override fun findById(id: Long): BoardTask? = null
    override fun save(task: BoardTask): BoardTask = task.copy(id = 1)
    override fun update(task: BoardTask): BoardTask = task
    override fun deleteById(id: Long) {}
    override fun countByWorkspaceId(workspaceId: Long): Int = 0
}

@Repository
class BoardActivityStubRepository : BoardActivityRepository {
    override fun findByWorkspaceId(workspaceId: Long, limit: Int): List<BoardActivity> = emptyList()
    override fun save(activity: BoardActivity): BoardActivity = activity.copy(id = 1)
}
