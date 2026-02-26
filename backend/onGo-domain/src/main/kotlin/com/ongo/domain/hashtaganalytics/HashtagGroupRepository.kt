package com.ongo.domain.hashtaganalytics

interface HashtagGroupRepository {
    fun findByWorkspaceId(workspaceId: Long): List<HashtagGroup>
    fun findById(id: Long): HashtagGroup?
    fun save(group: HashtagGroup): HashtagGroup
    fun deleteById(id: Long)
}
