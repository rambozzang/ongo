package com.ongo.domain.contentversioning

interface ContentVersionGroupRepository {
    fun findByWorkspaceId(workspaceId: Long): List<ContentVersionGroup>
    fun findById(id: Long): ContentVersionGroup?
    fun save(group: ContentVersionGroup): ContentVersionGroup
    fun update(group: ContentVersionGroup): ContentVersionGroup
}
