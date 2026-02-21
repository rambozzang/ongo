package com.ongo.domain.workspace

interface WorkspaceRepository {
    fun findById(id: Long): Workspace?
    fun findBySlug(slug: String): Workspace?
    fun findByOwnerId(ownerId: Long): List<Workspace>
    fun findAccessibleByUserId(userId: Long): List<Workspace>
    fun save(workspace: Workspace): Workspace
    fun update(workspace: Workspace): Workspace
    fun delete(id: Long)
    fun countByOwnerId(ownerId: Long): Int
}
