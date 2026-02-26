package com.ongo.domain.collaborationboard

interface BoardTaskRepository {
    fun findByWorkspaceId(workspaceId: Long): List<BoardTask>
    fun findByWorkspaceIdAndColumn(workspaceId: Long, columnType: String): List<BoardTask>
    fun findById(id: Long): BoardTask?
    fun save(task: BoardTask): BoardTask
    fun update(task: BoardTask): BoardTask
    fun deleteById(id: Long)
    fun countByWorkspaceId(workspaceId: Long): Int
}
