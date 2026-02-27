package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentversioning.*
import org.springframework.stereotype.Repository

@Repository
class ContentVersionGroupStubRepository : ContentVersionGroupRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<ContentVersionGroup> = emptyList()
    override fun findById(id: Long): ContentVersionGroup? = null
    override fun save(group: ContentVersionGroup): ContentVersionGroup = group.copy(id = 1)
    override fun update(group: ContentVersionGroup): ContentVersionGroup = group
}

@Repository
class ContentVersionStubRepository : ContentVersionRepository {
    override fun findByGroupId(groupId: Long): List<ContentVersion> = emptyList()
    override fun findById(id: Long): ContentVersion? = null
    override fun save(version: ContentVersion): ContentVersion = version.copy(id = 1)
}
